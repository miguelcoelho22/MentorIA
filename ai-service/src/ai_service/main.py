from fastapi import FastAPI

app = FastAPI(
    title="MentorIA AI Service",
    description="Serviço de inteligência artificial do MentorIA",
    version="1.0.0"
)

@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "ai-service"
    }