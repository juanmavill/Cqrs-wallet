#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./run-sweep-aws.sh <IP_CQRS> <IP_MONO> <IP_DATA> [duration_s] [rampup_s] [levels_csv]
# Example:
#   ./run-sweep-aws.sh 1.2.3.4 5.6.7.8 9.10.11.12 120 20 50,100,200,350,500

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <IP_CQRS> <IP_MONO> <IP_DATA> [duration_s] [rampup_s] [levels_csv]"
  exit 1
fi

IP_CQRS="$1"
IP_MONO="$2"
IP_DATA="$3"
DURATION="${4:-120}"
RAMPUP="${5:-20}"
THREAD_LEVELS="${6:-50,100,200,350,500}"
PEM="${PEM:-$HOME/.aws-keys/labsuser.pem}"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JMETER_DIR="$PROJECT_DIR/jmeter"
JMETER_BIN="${HOME}/apache-jmeter-5.6.3/bin/jmeter"
STAMP="$(date +%Y%m%d-%H%M%S)"
CSV="$JMETER_DIR/sweep-aws-${STAMP}.csv"

reset_data() {
  ssh -i "$PEM" -o StrictHostKeyChecking=no ec2-user@"$IP_DATA" bash << 'ENDSSH' || true
docker exec wallet-mysql mysql -uroot -prootpass wallet_db \
  -e 'UPDATE accounts SET balance=10000.00; DELETE FROM transactions;'
docker exec wallet-mongodb mongosh wallet_read --quiet \
  --eval 'db.balances.updateMany({}, {$set: {balance: 10000.00}})'
ENDSSH
  sleep 3
}

extract_to_csv() {
  local stats_file=$1 threads=$2 arch=$3
  python3 - <<PY
import json
with open("$stats_file") as f:
    data = json.load(f)
label_map = {
    "POST /api/transactions": "POST",
    "GET /api/balance/{id}":  "GET",
    "Total":                  "TOTAL",
}
order = ["TOTAL", "POST", "GET"]
rows = []
for tx, m in data.items():
    short = label_map.get(tx, tx.replace(",", " "))
    rows.append((short, m))
rows.sort(key=lambda r: order.index(r[0]) if r[0] in order else 99)
with open("$CSV", "a") as out:
    for short, m in rows:
        out.write("{t},{a},{tx},{n},{e:.4f},{mean:.2f},{p50:.2f},{p95:.2f},{p99:.2f},{mx:.2f},{tp:.2f}\n".format(
            t=$threads, a="$arch", tx=short,
            n=m["sampleCount"], e=m["errorPct"],
            mean=m["meanResTime"], p50=m["medianResTime"],
            p95=m["pct2ResTime"], p99=m["pct3ResTime"],
            mx=m["maxResTime"], tp=m["throughput"]))
PY
}

run_jmeter() {
  local label=$1 write_host=$2 write_port=$3 read_host=$4 read_port=$5 threads=$6
  local outdir="$JMETER_DIR/reports/sweep-aws-${STAMP}-${label}-${threads}t"
  local jtl="$JMETER_DIR/sweep-aws-${STAMP}-${label}-${threads}t.jtl"
  rm -rf "$outdir" "$jtl"
  echo ">>> Running ${label} threads=${threads} duration=${DURATION}s rampup=${RAMPUP}s"
  "$JMETER_BIN" -n -t "$JMETER_DIR/wallet.jmx" \
    -Jhost.write="$write_host" -Jport.write="$write_port" \
    -Jhost.read="$read_host"   -Jport.read="$read_port" \
    -Jthreads="$threads" -Jrampup="$RAMPUP" -Jduration="$DURATION" \
    -l "$jtl" -e -o "$outdir" 2>&1 | tail -4
  extract_to_csv "$outdir/statistics.json" "$threads" "$label"
}

echo "threads,arch,transaction,samples,error_pct,mean_ms,p50_ms,p95_ms,p99_ms,max_ms,throughput_rps" > "$CSV"

IFS=',' read -ra LEVELS <<< "$THREAD_LEVELS"
TOTAL_RUNS=$(( ${#LEVELS[@]} * 2 ))
RUN_IDX=0
START_TS=$(date +%s)

for t in "${LEVELS[@]}"; do
  echo ""
  echo "================================================="
  echo " THREADS = $t   (run $((RUN_IDX+1))-$((RUN_IDX+2)) of $TOTAL_RUNS)"
  echo "================================================="

  reset_data
  run_jmeter "mono" "$IP_MONO" 8080 "$IP_MONO" 8080 "$t"
  RUN_IDX=$((RUN_IDX+1))

  reset_data
  run_jmeter "cqrs" "$IP_CQRS" 8081 "$IP_CQRS" 8082 "$t"
  RUN_IDX=$((RUN_IDX+1))
done

END_TS=$(date +%s)
ELAPSED=$(( END_TS - START_TS ))
echo ""
echo "================================================="
echo " Sweep completed in $((ELAPSED/60))m $((ELAPSED%60))s"
echo " CSV: $CSV"
echo "================================================="
column -t -s ',' "$CSV"
