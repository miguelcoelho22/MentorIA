import sys


def main() -> None:
    from ai_service.ingest import main as ingest_main

    if len(sys.argv) > 1 and sys.argv[1] == "ingest":
        sys.exit(ingest_main())
    print("Uso: ai-service ingest")
    sys.exit(2)
