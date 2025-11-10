> 🌐 Chinese Version: [README_docker_cn.md](README_docker_cn.md)


# Build and start all services
docker-compose up -d --build

# Check service status
docker-compose ps

# View logs
docker-compose logs -f

# View portfolio-receiver logs
docker-compose logs -f portfolio-receiver

# Sample Output
![alt text](docker-compose-output.png)