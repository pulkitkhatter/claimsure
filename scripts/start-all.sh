#!/usr/bin/env bash
# Starts infra (Docker) then every service, in dependency order. Logs go to ./logs
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p logs

# Host ports for the infra can be changed if the defaults are taken, e.g.  KAFKA_PORT=9192 MONGO_PORT=27018 ./scripts/start-all.sh
export KAFKA_PORT="${KAFKA_PORT:-9092}" MONGO_PORT="${MONGO_PORT:-27017}"
export KAFKA_BROKERS="localhost:$KAFKA_PORT"

docker compose up -d
echo "Waiting for MongoDB..."
until docker exec claimsure-mongo mongosh --quiet --eval 'db.adminCommand("ping").ok' >/dev/null 2>&1; do sleep 2; done

./mvnw -q -DskipTests install

start() { # name, port
  echo "Starting $1 on :$2"
  nohup java -jar "$1/target/$1-1.0.0.jar" > "logs/$1.log" 2>&1 &
  echo $! > "logs/$1.pid"
}
wait_for() { until curl -sf "http://localhost:$1/actuator/health" >/dev/null; do sleep 2; done; }

start discovery-server 9761; wait_for 9761
for s in "identity-service 9081" "policy-service 9082" "claims-service 9083" "notification-service 9084"; do
  start $s
done
for p in 9081 9082 9083 9084; do wait_for $p; done
start api-gateway 9080; wait_for 9080

echo
echo "ClaimSure is up:  http://localhost:9080"
echo "Demo logins (password Passw0rd!demo): customer@claimsure.test | adjuster@claimsure.test | admin@claimsure.test"
echo "Swagger UIs: http://localhost:9081|9082|9083|9084/swagger-ui.html   Eureka: http://localhost:9761"
