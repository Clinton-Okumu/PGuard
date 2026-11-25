# Stage 1: Build the PGuard Go backend from repository root
FROM golang:1.24-alpine AS builder

# Allow Go to automatically download newer toolchain versions (go.mod uses go 1.26.0)
ENV GOTOOLCHAIN=auto

WORKDIR /app

RUN apk add --no-cache git ca-certificates tzdata

# Cache Go dependencies
COPY Backend/go.mod Backend/go.sum ./
RUN go mod download

# Copy Backend source and compile static binary
COPY Backend/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /app/bin/server ./main.go

# Stage 2: Minimal runtime image
FROM alpine:3.21

RUN apk add --no-cache ca-certificates tzdata && \
    adduser -D -g '' appuser

WORKDIR /app

# Accept build-time ARGs from platforms (like Orkestr) that pass non-"Runtime only" variables as build args
ARG PORT=8080
ARG HOST=0.0.0.0
ARG SERVER_BASE_URL
ARG DATABASE_URL
ARG DATABASE_URL_UNPOOLED
ARG JWT_SECRET
ARG JWT_ACCESS_DURATION_MINUTES=10080
ARG JWT_REFRESH_DURATION_DAYS=60
ARG DEV_MOCK_AUTH=false
ARG GOOGLE_CLIENT_IDS
ARG RESEND_API_KEY
ARG RESEND_FROM_EMAIL
ARG RESEND_SANDBOX_FALLBACK_EMAIL

ENV PORT=${PORT} \
    HOST=${HOST} \
    SERVER_BASE_URL=${SERVER_BASE_URL} \
    DATABASE_URL=${DATABASE_URL} \
    DATABASE_URL_UNPOOLED=${DATABASE_URL_UNPOOLED} \
    JWT_SECRET=${JWT_SECRET} \
    JWT_ACCESS_DURATION_MINUTES=${JWT_ACCESS_DURATION_MINUTES} \
    JWT_REFRESH_DURATION_DAYS=${JWT_REFRESH_DURATION_DAYS} \
    DEV_MOCK_AUTH=${DEV_MOCK_AUTH} \
    GOOGLE_CLIENT_IDS=${GOOGLE_CLIENT_IDS} \
    RESEND_API_KEY=${RESEND_API_KEY} \
    RESEND_FROM_EMAIL=${RESEND_FROM_EMAIL} \
    RESEND_SANDBOX_FALLBACK_EMAIL=${RESEND_SANDBOX_FALLBACK_EMAIL}

COPY --from=builder /app/bin/server /app/server

USER appuser

EXPOSE 8080

CMD ["/app/server"]
