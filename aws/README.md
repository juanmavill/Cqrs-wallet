# AWS Deployment — CQRS Wallet

Three EC2 instances in `us-east-1`, all within a single VPC with a public subnet.

## Architecture

| Instance | Services | Exposed ports |
|---|---|---|
| **ec2-data** | MySQL, MongoDB, RabbitMQ | Internal only (3306, 27017, 5672) |
| **ec2-cqrs** | command-service, query-service | 8081, 8082 |
| **ec2-mono** | monolith-reference | 8080 |

JMeter runs from a local machine targeting the public IPs of `ec2-cqrs` and `ec2-mono`.

---

## Prerequisites

- Docker Hub account with the three service images pushed (see [Building images](#building-images))
- AWS Academy Learner Lab session active
- SSH key pair (`vockey`) downloaded as a `.pem` file
- JMeter 5.6.3 installed locally

---

## Building images

```bash
export DOCKERHUB_USER=<your-dockerhub-username>
docker login
cd aws
./build-and-push.sh
```

---

## AWS setup

### Security group

Create a security group named `cqrs-wallet-sg` in the default VPC with the following inbound rules:

| Protocol | Port | Source | Purpose |
|---|---|---|---|
| TCP | 22 | 0.0.0.0/0 | SSH |
| TCP | 8080 | 0.0.0.0/0 | Monolith |
| TCP | 8081 | 0.0.0.0/0 | Command service |
| TCP | 8082 | 0.0.0.0/0 | Query service |
| TCP | 15672 | 0.0.0.0/0 | RabbitMQ management UI |
| TCP | 3306 | sg-self | MySQL (inter-instance) |
| TCP | 27017 | sg-self | MongoDB (inter-instance) |
| TCP | 5672 | sg-self | RabbitMQ AMQP (inter-instance) |

`sg-self` means the security group itself as source, allowing traffic only between instances in the same group.

### EC2 instances

Launch three instances with these settings:

- **AMI**: Amazon Linux 2023
- **Type**: t3.small
- **Key pair**: `vockey`
- **Security group**: `cqrs-wallet-sg`
- **Storage**: 8 GB gp3
- **Names**: `ec2-data`, `ec2-cqrs`, `ec2-mono`

---

## Deployment

### 1. Install Docker on each instance

Run on each of the three instances:

```bash
ssh -i ~/.aws-keys/labsuser.pem ec2-user@<PUBLIC_IP>

sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
sudo curl -sL "https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64" \
     -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
exit
```

Reconnect after exit so the `docker` group takes effect.

### 2. Deploy ec2-data

```bash
scp -i ~/.aws-keys/labsuser.pem -r aws/ec2-data/ ec2-user@<IP_DATA>:~/
ssh -i ~/.aws-keys/labsuser.pem ec2-user@<IP_DATA>
cd ec2-data && docker-compose up -d
docker-compose ps    # all three should reach healthy within ~30s
```

### 3. Deploy ec2-cqrs

```bash
scp -i ~/.aws-keys/labsuser.pem -r aws/ec2-cqrs/ ec2-user@<IP_CQRS>:~/
ssh -i ~/.aws-keys/labsuser.pem ec2-user@<IP_CQRS>
cd ec2-cqrs
DOCKERHUB_USER=<user> DATA_HOST=<PRIVATE_IP_DATA> docker-compose up -d
sleep 30
curl http://localhost:8082/api/balance/ACC001
```

### 4. Deploy ec2-mono

```bash
scp -i ~/.aws-keys/labsuser.pem -r aws/ec2-mono/ ec2-user@<IP_MONO>:~/
ssh -i ~/.aws-keys/labsuser.pem ec2-user@<IP_MONO>
cd ec2-mono
DOCKERHUB_USER=<user> DATA_HOST=<PRIVATE_IP_DATA> docker-compose up -d
sleep 30
curl http://localhost:8080/api/balance/ACC001
```

### 5. Verify from local machine

```bash
curl http://<IP_MONO>:8080/api/balance/ACC001
curl http://<IP_CQRS>:8082/api/balance/ACC001
curl -X POST http://<IP_CQRS>:8081/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"ACC001","amount":100,"type":"DEBIT"}'
```

All three should return HTTP 200.

---

## Running the benchmark

```bash
cd jmeter
./run-sweep-aws.sh <IP_CQRS> <IP_MONO> <IP_DATA> [duration_s] [rampup_s] [levels_csv]
# defaults: duration=120, rampup=20, levels=50,100,200,350,500
```

Results are written to `jmeter/sweep-aws-<timestamp>.csv`.

---

## Tear-down

Terminate all three instances via the EC2 console and verify no resources remain billable under EC2 → Instances, Volumes, and Elastic IPs.

---

## Troubleshooting

| Symptom | Likely cause | Resolution |
|---|---|---|
| Connection refused from JMeter | Security group missing inbound rule | Check ports 8080–8082 |
| Spring service exits on startup | Insufficient RAM | Reduce `JAVA_TOOL_OPTIONS` to `-Xmx256m` |
| Unknown host in Spring logs | `DATA_HOST` not set or wrong | Verify the private IP of ec2-data |
| MySQL access denied | Wrong password | Confirm `rootpass` in compose file |
| Query service returns stale data | MongoSeedRunner ran before Mongo was ready | Restart query service: `docker-compose restart query-service` |
