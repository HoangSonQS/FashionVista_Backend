# Migration Guide: Moving to Docker Architecture

Follow these steps for a safe transition. All commands should be run from inside the `FashionVista_Backend` directory.

## 1. Preparation
1.  **Backup Database**:
    ```bash
    pg_dump -U postgres sixthsoul_test > backup.sql
    ```

## 2. Phase 1: Parallel Deployment (Testing)
1.  **Launch Stack**:
    ```bash
    docker-compose up -d
    ```
2.  **Import Data**:
    ```bash
    cat backup.sql | docker exec -i fashionvista-db psql -U sixthsoul -d sixthsouldb
    ```
3.  **Verify**:
    ```bash
    docker exec -it fashionvista-db psql -U sixthsoul -d sixthsouldb -c "\dt"
    curl -k https://localhost:8443/health
    ```

## 3. Phase 2: Switch-Over
1.  **Stop Host Nginx**: `sudo systemctl stop nginx`
2.  **Update Ports** in `docker-compose.yml` to `80:80` and `443:443`.
3.  **Re-deploy**: `docker-compose up -d`

## 4. Rollback
1. `docker-compose down`
2. `sudo systemctl start nginx`
