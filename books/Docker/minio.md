docker-compose.yaml

```yaml
version: '3.7'
services:
 minio:
   image: minio/minio:latest
   command: server --console-address ":9001" /data/
   ports:
     - "9000:9000"
     - "9001:9001"
   environment:
     MINIO_ROOT_USER: ozontech
     MINIO_ROOT_PASSWORD: minio123
   volumes:
     - minio-storage:/data
   restart: always
   healthcheck:
     test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
     interval: 30s
     timeout: 20s
     retries: 3
volumes:
 minio-storage:
```

docker-compose -p minio up -d

```
docker run --restart=always minio-minio-1
```

docker ps -a

/var/lib/docker/volumes/minio_minio-storage