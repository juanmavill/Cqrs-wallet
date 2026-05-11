#!/usr/bin/env bash
set -euo pipefail

THREADS="${1:-200}"
DURATION="${2:-300}"
RAMPUP="${3:-30}"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JMETER_DIR="$PROJECT_DIR/jmeter"
JMETER_BIN="${HOME}/apache-jmeter-5.6.3/bin/jmeter"

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
  echo ">>> Reset: docker-compose down -v && up -d"
  docker-compose down -v >/dev/null 2>&1 || true
  docker-compose up -d >/dev/null
  echo -n ">>> Esperando a que los 3 servicios estén listos"
  wait_port 8080
  wait_port 8081
  wait_port 8082
  echo ""
  # Margen extra para que el MongoSeedRunner siembre las 100 cuentas
  sleep 5
}

run_jmeter() {
  local label="$1" write_port="$2" read_port="$3"
  local outdir="$JMETER_DIR/reports/${label}"
  local jtl="$JMETER_DIR/${label}.jtl"
  rm -rf "$outdir" "$jtl"
  echo ">>> Corriendo ${label} (write=${write_port}, read=${read_port}, threads=${THREADS}, dur=${DURATION}s)"
  "$JMETER_BIN" -n -t "$JMETER_DIR/wallet.jmx" \
    -Jhost.write=localhost -Jport.write="$write_port" \
    -Jhost.read=localhost  -Jport.read="$read_port" \
    -Jthreads="$THREADS" -Jrampup="$RAMPUP" -Jduration="$DURATION" \
    -l "$jtl" -e -o "$outdir"
  echo ">>> Reporte: $outdir/index.html"
}

echo "=========================================="
echo " Benchmark JUSTO  | threads=$THREADS  dur=${DURATION}s  rampup=${RAMPUP}s"
echo "=========================================="

reset_stack
run_jmeter "mono-fair"  8080 8080

reset_stack
run_jmeter "cqrs-fair"  8081 8082

echo ""
echo "=========================================="
echo "Listo. Compara:"
echo "  explorer.exe $JMETER_DIR/reports/mono-fair/index.html"
echo "  explorer.exe $JMETER_DIR/reports/cqrs-fair/index.html"
echo "=========================================="
