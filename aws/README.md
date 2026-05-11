# Despliegue AWS — CQRS Wallet

Tres instancias EC2 en `us-east-1`, todo en una VPC con subnet pública.

## Arquitectura

| EC2 | Servicios | Puertos públicos |
|---|---|---|
| **ec2-data**  | MySQL, MongoDB, RabbitMQ | (solo internos: 3306, 27017, 5672) |
| **ec2-cqrs**  | command-service, query-service | 8081, 8082 |
| **ec2-mono**  | monolith-reference | 8080 |

JMeter corre **desde tu laptop** contra las IPs públicas de `ec2-cqrs` y `ec2-mono`.

---

## Fase 1 — Prep local (en tu WSL)

### 1.1 Crear cuenta Docker Hub (gratis)
https://hub.docker.com → Sign Up. Anotá tu usuario.

### 1.2 Login Docker
```bash
docker login
```

### 1.3 Build + push imágenes
```bash
export DOCKERHUB_USER=tuusuario
cd /home/juanm/cqrs-wallet/aws
./build-and-push.sh
```
Tarda ~5-10 min la primera vez (push de ~150 MB por imagen).

---

## Fase 2 — AWS setup

### 2.1 Configurar billing alert (CRÍTICO, hacelo primero)
Consola AWS → Budgets → Create budget → "Monthly cost" → $5 USD → tu email.

### 2.2 Crear key pair (para SSH)
Consola AWS → EC2 → Key Pairs → Create key pair → nombre `cqrs-wallet-key` → tipo `RSA` → formato `.pem` → descargar.
```bash
mkdir -p ~/.aws-keys
mv ~/Downloads/cqrs-wallet-key.pem ~/.aws-keys/   # ajustá la ruta a tu Downloads de Windows
chmod 400 ~/.aws-keys/cqrs-wallet-key.pem
```

### 2.3 Crear security group
Consola AWS → EC2 → Security Groups → Create security group:
- Nombre: `cqrs-wallet-sg`
- VPC: default
- **Inbound rules**:
  | Type | Protocol | Port | Source | Descripción |
  |---|---|---|---|---|
  | SSH | TCP | 22 | Mi IP | Acceso SSH |
  | Custom TCP | TCP | 8080 | 0.0.0.0/0 | Monolito |
  | Custom TCP | TCP | 8081 | 0.0.0.0/0 | Command |
  | Custom TCP | TCP | 8082 | 0.0.0.0/0 | Query |
  | Custom TCP | TCP | 3306 | sg-self | MySQL inter-EC2 |
  | Custom TCP | TCP | 27017 | sg-self | Mongo inter-EC2 |
  | Custom TCP | TCP | 5672 | sg-self | RabbitMQ inter-EC2 |
  | Custom TCP | TCP | 15672 | Mi IP | RabbitMQ UI |
- **Outbound rules**: All traffic 0.0.0.0/0 (default)

**Nota:** "sg-self" significa que la fuente es el mismo security group (que las EC2 hablen entre sí). Se agrega seleccionando el security group recién creado como fuente.

### 2.4 Lanzar 3 EC2
Por cada una: EC2 → Launch Instance:
- AMI: **Amazon Linux 2023**
- Tipo: **t3.small** (2 vCPU, 2 GB RAM)
- Key pair: `cqrs-wallet-key`
- Security group: `cqrs-wallet-sg`
- Storage: 8 GB gp3 (default)
- Tags: `Name=ec2-data` / `ec2-cqrs` / `ec2-mono`

Anotá las IPs:
| Instancia | IP pública | IP privada |
|---|---|---|
| ec2-data | _____ | _____ |
| ec2-cqrs | _____ | _____ |
| ec2-mono | _____ | _____ |

---

## Fase 3 — Deploy

### 3.1 Instalar Docker en cada EC2
Para cada una de las 3 IPs públicas:
```bash
ssh -i ~/.aws-keys/cqrs-wallet-key.pem ec2-user@<IP_PUBLICA>

# dentro de la EC2:
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
sudo curl -L "https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
exit
```
Reconectá por SSH para que el grupo `docker` tome efecto.

### 3.2 Desplegar ec2-data
```bash
scp -i ~/.aws-keys/cqrs-wallet-key.pem -r /home/juanm/cqrs-wallet/aws/ec2-data/ ec2-user@<IP_PUBLICA_DATA>:~/
ssh -i ~/.aws-keys/cqrs-wallet-key.pem ec2-user@<IP_PUBLICA_DATA>
cd ec2-data
docker-compose up -d
docker-compose ps    # los 3 deben estar healthy en ~30s
exit
```

### 3.3 Desplegar ec2-cqrs
```bash
scp -i ~/.aws-keys/cqrs-wallet-key.pem -r /home/juanm/cqrs-wallet/aws/ec2-cqrs/ ec2-user@<IP_PUBLICA_CQRS>:~/
ssh -i ~/.aws-keys/cqrs-wallet-key.pem ec2-user@<IP_PUBLICA_CQRS>
cd ec2-cqrs
export DOCKERHUB_USER=tuusuario
export DATA_HOST=<IP_PRIVADA_DATA>
docker-compose up -d
sleep 30
curl http://localhost:8082/api/balance/ACC001    # debe responder JSON
exit
```

### 3.4 Desplegar ec2-mono
```bash
scp -i ~/.aws-keys/cqrs-wallet-key.pem -r /home/juanm/cqrs-wallet/aws/ec2-mono/ ec2-user@<IP_PUBLICA_MONO>:~/
ssh -i ~/.aws-keys/cqrs-wallet-key.pem ec2-user@<IP_PUBLICA_MONO>
cd ec2-mono
export DOCKERHUB_USER=tuusuario
export DATA_HOST=<IP_PRIVADA_DATA>
docker-compose up -d
sleep 30
curl http://localhost:8080/api/balance/ACC001    # debe responder JSON
exit
```

### 3.5 Verificar desde tu laptop
```bash
curl http://<IP_PUBLICA_MONO>:8080/api/balance/ACC001
curl http://<IP_PUBLICA_CQRS>:8082/api/balance/ACC001
curl -X POST http://<IP_PUBLICA_CQRS>:8081/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"ACC001","amount":100,"type":"DEBIT"}'
```
Si los tres responden 200, está listo para el benchmark.

---

## Fase 4 — Benchmark desde tu laptop

```bash
cd /home/juanm/cqrs-wallet/jmeter

# Smoke test (validar conectividad)
~/apache-jmeter-5.6.3/bin/jmeter -n -t wallet.jmx \
  -Jhost.write=<IP_PUBLICA_MONO> -Jport.write=8080 \
  -Jhost.read=<IP_PUBLICA_MONO>  -Jport.read=8080 \
  -Jthreads=20 -Jrampup=5 -Jduration=20 \
  -l aws-smoke.jtl -e -o reports/aws-smoke

# Sweep completo (editá run-sweep-benchmark.sh para que use las IPs públicas, o copiá y modificá)
# Ver más abajo "Script de sweep adaptado"
```

### Script de sweep adaptado
Te paso un `run-sweep-aws.sh` aparte cuando llegues a esta fase, con las IPs públicas hardcoded.

---

## Fase 5 — Tear-down (NO OLVIDAR)

```bash
# Desde la consola AWS:
# 1. EC2 → seleccionar las 3 instancias → Instance state → Terminate
# 2. Security Groups → Borrar cqrs-wallet-sg
# 3. Key Pairs → Borrar cqrs-wallet-key
# 4. Volumes (si quedaron orphan) → Delete
# 5. Verificá en Billing que no quede nada cobrándose

# Borrar imágenes Docker Hub (opcional, no cuesta nada dejarlas)
```

---

## Troubleshooting rápido

| Síntoma | Causa probable | Fix |
|---|---|---|
| `Connection refused` desde JMeter | Security group bloquea 8080-8082 | Revisá inbound rules |
| Spring service muere al arrancar | No alcanza la RAM | Bajá `JAVA_TOOL_OPTIONS` a `-Xmx384m` |
| `Unknown host` en logs Spring | `DATA_HOST` mal seteado | Verificá variable + IP privada |
| `Access denied` MySQL | password mal | rootpass / root (chequeá compose) |
| MongoSeedRunner falla | Mongo aún no listo cuando arranca query-service | Reiniciá solo query-service: `docker-compose restart query-service` |
