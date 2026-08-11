FROM python:3.12-slim

WORKDIR /app
COPY app.py config.example.json ./
RUN cp config.example.json config.json

ENV HOST=0.0.0.0
ENV PORT=8080
ENV CONFIG_PATH=/app/config.json

EXPOSE 8080
CMD ["python", "-u", "app.py"]
