"""CLI：python -m supplychain [--date 2026-10-28] [--varieties lc,si,ps] [--trigger-type MANUAL]

本地联调用（与 python -m 主服务触发等价）；生产由 Java XXL-JOB 经 HTTP 触发。
"""
import argparse
import asyncio
import json

from supplychain.service import parse_trade_date, run_gfex_collection


def main() -> None:
    parser = argparse.ArgumentParser(description='广期所仓单采集（发 MQ，不落库）')
    parser.add_argument('--date', default=None, help='交易日 yyyy-MM-dd 或 yyyyMMdd，缺省=今天')
    parser.add_argument('--varieties', default=None, help='品种代码逗号分隔（缺省 lc,si,ps）')
    parser.add_argument('--trigger-type', default='MANUAL', choices=['MANUAL', 'SCHEDULED'])
    args = parser.parse_args()

    varieties = args.varieties.split(',') if args.varieties else None
    summary = asyncio.run(run_gfex_collection(trade_date=parse_trade_date(args.date),
                                              varieties=varieties,
                                              trigger_type=args.trigger_type))
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
