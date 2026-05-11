#!/usr/bin/env bash
set -euo pipefail

# Uso:
#   ./run-sweep-benchmark.sh [duration_s] [rampup_s] [levels_csv]
# Defaults: duration=180, rampup=30, levels=50,100,200,500,1000
# Tiempo total estimado: ~(levels * 2 * (duration + 60)) segundos
#   con defaults  ≈ 5 * 2 * 240 = 40 min

DURATION="${1:-180}"
RAMPUP="${2:-30}"
THREAD_LEVELS="${3:-50,100,200,500,1000}"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JMETER_DIR="$PROJECT_DIR/jmeter"
JMETER_BIN="${HOME}/apache-jmeter-5.6.3/bin/jmeter"
STAMP="$(date +%Y%m%d-%H%M%S)"
CSV="$JMETER_DIR/sweep-${STAMP}.csv"

cd "$PROJECT_DIR"

wait_port() {
  local port=$1
  local tries=0
  while true; do
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://localhost:${port}/" 2>/dev/null || echo 000)
    if [[ "$code" =~ ^[2-5][0-9][0-9]$ ]]; then
      echo -n " [${port} OK]"
      return 0
    fi
    tries=$((tries+1))
    if (( tries > 120 )); then
      echo " [${port} TIMEOUT]"
      return 1
    fi
    sleep 2
    echo -n "."
  done
}

reset_stack() {
  echo ">>> Reset"
  docker-compose down -v >/dev/null 2>&1 || true
  docker-compose up -d >/dev/null
  echo -n ">>> Esperando readiness"
  wait_port 8080
  wait_port 8081
  wait_port 8082
  echo ""
  sleep 5
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
  local label=$1 write_port=$2 read_port=$3 threads=$4
  local outdir="$JMETER_DIR/reports/sweep-${STAMP}-${label}-${threads}t"
  local jtl="$JMETER_DIR/sweep-${STAMP}-${label}-${threads}t.jtl"
  rm -rf "$outdir" "$jtl"
  echo ">>> Corriendo ${label} threads=${threads} dur=${DURATION}s rampup=${RAMPUP}s"
  "$JMETER_BIN" -n -t "$JMETER_DIR/wallet.jmx" \
    -Jhost.write=localhost -Jport.write="$write_port" \
    -Jhost.read=localhost  -Jport.read="$read_port" \
    -Jthreads="$threads" -Jrampup="$RAMPUP" -Jduration="$DURATION" \
    -l "$jtl" -e -o "$outdir" 2>&1 | tail -4
  extract_to_csv "$outdir/statistics.json" "$threads" "$label"
}

# Header del CSV
echo "threads,arch,transaction,samples,error_pct,mean_ms,p50_ms,p95_ms,p99_ms,max_ms,throughput_rps" > "$CSV"

IFS=',' read -ra LEVELS <<< "$THREAD_LEVELS"
TOTAL_RUNS=$(( ${#LEVELS[@]} * 2 ))
RUN_IDX=0
START_TS=$(date +%s)

for t in "${LEVELS[@]}"; do
  echo ""
  echo "================================================="
  echo " THREADS = $t   (corrida $((RUN_IDX+1))-$((RUN_IDX+2)) de $TOTAL_RUNS)"
  echo "================================================="

  reset_stack
  run_jmeter "mono" 8080 8080 "$t"
  RUN_IDX=$((RUN_IDX+1))

  reset_stack
  run_jmeter "cqrs" 8081 8082 "$t"
  RUN_IDX=$((RUN_IDX+1))
done

END_TS=$(date +%s)
ELAPSED=$(( END_TS - START_TS ))
echo ""
echo "================================================="
echo " SWEEP TERMINADO en $((ELAPSED/60)) min $((ELAPSED%60)) s"
echo " CSV: $CSV"
echo "================================================="
column -t -s ',' "$CSV"
echo ""
echo "Tip: para gráficas rápidas filtra TOTAL/GET/POST por arch en Excel o:"
echo "  awk -F, '\$3==\"GET\"' \"$CSV\""
