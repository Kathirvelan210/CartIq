# Setup

Install Node.js, Java 17, Maven, Python and MySQL 8. Run npm ci at project root, then npm run web -- --port 8092. Backend: cd backend; mvn.cmd spring-boot:run (local H2 profile). AI: cd ai-service; create a virtual environment, pip install -r requirements.txt, then uvicorn app:app --host 127.0.0.1 --port 8001. Configure your own secrets using configuration/.env.example as a guide; it is not loaded automatically. Verify version compatibility and frontend/backend connection locally. See DATABASE_SETUP.md before enabling MySQL.
