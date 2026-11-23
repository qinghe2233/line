#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
预约下单并发压测脚本（只用 Python 标准库，无需 pip 安装）。

JWT 版本：先用种子账号登录拿 token，再用 Authorization: Bearer 调下单接口，
不再使用任何可伪造的用户标识请求头。

前置数据（二选一）：
  1) 执行种子脚本（会创建 200 个压测账号与未来 7 天时段）：
     mysql -uroot -p123456 --default-character-set=utf8mb4 \
       < src/main/resources/db/seed.sql
  2) 压测前重置目标时段（slot_id 先查询，取明天 10:00 的剪发时段）：
     SELECT id FROM time_slot_stock
      WHERE store_id=1 AND service_id=1 AND resource_id=1
        AND slot_date=CURDATE()+INTERVAL 1 DAY AND start_time='10:00:00';
     UPDATE time_slot_stock SET total_stock=5, remain_stock=5, status=1 WHERE id=<slot_id>;
     DELETE FROM booking_order WHERE slot_id=<slot_id>;

测试一 concurrency：200 个不同用户、不同幂等键，同时抢同一个时段的 5 个名额。
  期望：5 个成功（code=0），195 个 SLOT_SOLD_OUT（code=40002），
        5 个订单号互不相同，数据库 remain_stock = 0 且订单数 = 5。

测试二 idempotent：50 个线程用同一个用户 + 同一个 Idempotency-Key 重复提交。
  期望：全部 code=0，只产生 1 个订单号，库存只扣 1。

运行：
  python scripts/load_test_booking.py concurrency --slot-id 1
  python scripts/load_test_booking.py idempotent  --slot-id 1

可选数据库断言（装了 pymysql 时）：
  pip install pymysql
  python scripts/load_test_booking.py concurrency --slot-id 1 --check-db --mysql-password 123456
"""
import argparse
import json
import statistics
import sys
import threading
import time
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from urllib import error, request

SUCCESS_CODE = 0
SOLD_OUT_CODE = 40002
CONCURRENCY = 200
STOCK = 5
IDEMPOTENT_THREADS = 50
DEFAULT_PHONE_START = 13800001001   # 种子脚本生成的压测账号起始手机号
DEFAULT_PASSWORD = "123456"


def login(base_url, phone, password):
    """登录拿 JWT；40002 之外的失败直接抛出，避免压测跑成一片 401 还看不出来。"""
    body = json.dumps({"phone": phone, "password": password}).encode("utf-8")
    req = request.Request(
        base_url.rstrip("/") + "/api/auth/login",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with request.urlopen(req, timeout=15) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != SUCCESS_CODE:
        raise RuntimeError(f"登录失败 phone={phone} code={payload.get('code')} message={payload.get('message')}")
    return payload["data"]["token"]


def fetch_tokens(base_url, phones, password):
    """并发登录：BCrypt 校验是 CPU 密集的，串行登录 200 个账号要十几秒"""
    with ThreadPoolExecutor(max_workers=16) as pool:
        return list(pool.map(lambda phone: login(base_url, phone, password), phones))


def create_appointment(base_url, slot_id, token, idem_key, barrier, results):
    body = json.dumps({"slotId": slot_id}).encode("utf-8")
    req = request.Request(
        base_url.rstrip("/") + "/api/appointments",
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Idempotency-Key": idem_key,
            "Authorization": f"Bearer {token}",
        },
    )
    # 用栅栏把所有线程卡在同一时刻，尽量制造真实的并发竞争
    barrier.wait(timeout=30)
    started = time.perf_counter()
    try:
        with request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            results.append((resp.status, payload.get("code"), payload, (time.perf_counter() - started) * 1000))
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", "ignore")
        results.append((exc.code, -1, {"raw": body_text}, (time.perf_counter() - started) * 1000))
    except Exception as exc:  # 连接失败/超时也记录下来，不中断压测
        results.append((0, -1, {"error": repr(exc)}, (time.perf_counter() - started) * 1000))


def run_round(base_url, slot_id, tokens, keys):
    threads = len(tokens)
    barrier = threading.Barrier(threads)
    results = []
    with ThreadPoolExecutor(max_workers=threads) as pool:
        for token, key in zip(tokens, keys):
            pool.submit(create_appointment, base_url, slot_id, token, key, barrier, results)
    return results


def percentile(values, ratio):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(len(ordered) * ratio))
    return ordered[index]


def print_stats(results):
    latencies = [item[3] for item in results]
    print(f"  请求数={len(results)}  HTTP200={sum(1 for r in results if r[0] == 200)}")
    print(f"  code 分布={dict(Counter(r[1] for r in results))}")
    print(f"  耗时(ms) P50={percentile(latencies, 0.50):.1f} "
          f"P95={percentile(latencies, 0.95):.1f} "
          f"P99={percentile(latencies, 0.99):.1f} "
          f"max={max(latencies):.1f} avg={statistics.fmean(latencies):.1f}")


def fetch_db_state(args, slot_id):
    """数据库断言是可选的：没装 pymysql 时只做接口层断言。"""
    try:
        import pymysql
    except ImportError:
        print("  [跳过] 未安装 pymysql，跳过数据库断言")
        return None
    conn = pymysql.connect(
        host=args.mysql_host, port=args.mysql_port,
        user=args.mysql_user, password=args.mysql_password,
        database="booking_queue", charset="utf8mb4",
    )
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT remain_stock FROM time_slot_stock WHERE id = %s", (slot_id,))
            remain = cursor.fetchone()[0]
            cursor.execute("SELECT COUNT(*), COUNT(DISTINCT order_no) FROM booking_order WHERE slot_id = %s", (slot_id,))
            order_count, order_no_count = cursor.fetchone()
        return {"remain_stock": remain, "order_count": order_count, "order_no_count": order_no_count}
    finally:
        conn.close()


def check(name, condition, detail):
    print(f"  [{'PASS' if condition else 'FAIL'}] {name}: {detail}")
    return condition


def run_concurrency_test(args):
    print(f"== 并发抢名额：{CONCURRENCY} 线程抢 {STOCK} 个名额 ==")
    phones = [str(DEFAULT_PHONE_START + i) for i in range(CONCURRENCY)]
    print(f"  登录 {len(phones)} 个种子账号 ...")
    tokens = fetch_tokens(args.base_url, phones, args.password)
    keys = [str(uuid.uuid4()) for _ in range(CONCURRENCY)]
    results = run_round(args.base_url, args.slot_id, tokens, keys)
    print_stats(results)

    codes = Counter(r[1] for r in results)
    order_nos = {r[2].get("data", {}).get("orderNo") for r in results if r[1] == SUCCESS_CODE}
    passed = [
        check("全部 HTTP 200", all(r[0] == 200 for r in results), f"非 200 数量={sum(1 for r in results if r[0] != 200)}"),
        check("成功数 = 库存数", codes.get(SUCCESS_CODE, 0) == STOCK, f"成功={codes.get(SUCCESS_CODE, 0)}，期望={STOCK}"),
        check("售罄数 = 总请求 - 库存", codes.get(SOLD_OUT_CODE, 0) == CONCURRENCY - STOCK,
              f"售罄={codes.get(SOLD_OUT_CODE, 0)}，期望={CONCURRENCY - STOCK}"),
        check("订单号不重复", len(order_nos) == STOCK, f"不同订单号={len(order_nos)}，期望={STOCK}"),
    ]

    db_state = fetch_db_state(args, args.slot_id) if args.check_db else None
    if db_state is not None:
        passed.append(check("库存扣到 0", db_state["remain_stock"] == 0, f"remain_stock={db_state['remain_stock']}"))
        passed.append(check("订单数 = 5", db_state["order_count"] == STOCK, f"订单数={db_state['order_count']}"))
        passed.append(check("无重复单号", db_state["order_no_count"] == STOCK, f"不同单号={db_state['order_no_count']}"))

    return all(passed)


def run_idempotent_test(args):
    print(f"== 幂等验证：{IDEMPOTENT_THREADS} 线程使用同一个 Idempotency-Key ==")
    token = login(args.base_url, str(DEFAULT_PHONE_START), args.password)
    key = str(uuid.uuid4())
    results = run_round(args.base_url, args.slot_id, [token] * IDEMPOTENT_THREADS, [key] * IDEMPOTENT_THREADS)
    print_stats(results)

    codes = Counter(r[1] for r in results)
    order_nos = {r[2].get("data", {}).get("orderNo") for r in results if r[1] == SUCCESS_CODE}
    passed = [
        check("全部 HTTP 200", all(r[0] == 200 for r in results), f"非 200 数量={sum(1 for r in results if r[0] != 200)}"),
        check("全部返回成功", codes.get(SUCCESS_CODE, 0) == IDEMPOTENT_THREADS, f"成功={codes.get(SUCCESS_CODE, 0)}"),
        check("只产生一个订单号", len(order_nos) == 1, f"不同订单号={len(order_nos)}，期望=1"),
    ]

    db_state = fetch_db_state(args, args.slot_id) if args.check_db else None
    if db_state is not None:
        passed.append(check("库存只扣 1", db_state["remain_stock"] == STOCK - 1, f"remain_stock={db_state['remain_stock']}"))

    return all(passed)


def main():
    parser = argparse.ArgumentParser(description="预约下单并发/幂等压测")
    parser.add_argument("mode", choices=["concurrency", "idempotent"])
    parser.add_argument("--slot-id", type=int, required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="种子账号统一密码")
    parser.add_argument("--check-db", action="store_true")
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default="123456")
    args = parser.parse_args()

    ok = run_concurrency_test(args) if args.mode == "concurrency" else run_idempotent_test(args)
    print("== 结果：" + ("PASS" if ok else "FAIL") + " ==")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
