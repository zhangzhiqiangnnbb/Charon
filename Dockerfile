# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the application, skipping tests for speed in this context
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Python 3, pip, FFmpeg, and system dependencies for OpenCV/Pyzbar
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-venv \
    ffmpeg \
    libzbar0 \
    libsm6 \
    libxext6 \
    && rm -rf /var/lib/apt/lists/*

# Set up a virtual environment for Python
ENV VIRTUAL_ENV=/app/venv
RUN python3 -m venv $VIRTUAL_ENV
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

# Install Python dependencies
COPY scripts/requirements.txt ./scripts/requirements.txt
RUN pip install --no-cache-dir -r scripts/requirements.txt

# Copy the built artifact from the build stage
COPY --from=build /app/target/*.jar app.jar
# Copy the python scripts
COPY scripts ./scripts

# Create data directories
RUN mkdir -p /data/video-qrcode /data/video-store

# Set environment variables for the application
ENV APP_WORKDIR=/data/video-qrcode
ENV APP_STORAGE_LOCAL_BASE_DIR=/data/video-store
ENV APP_PYTHON_CMD=python
ENV APP_FFMPEG_CMD=ffmpeg

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
