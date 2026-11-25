# Stage 1: Build the Go binary
FROM golang:1.24-alpine AS builder

# Allow Go to automatically download newer toolchain versions if go.mod specifies a newer patch/minor release
ENV GOTOOLCHAIN=auto

WORKDIR /app

# Install git and CA certificates for fetching modules and making HTTPS calls (Google Auth, Resend, Neon SSL)
RUN apk add --no-cache git ca-certificates tzdata

# Cache dependencies first
COPY go.mod go.sum ./
RUN go mod download

# Copy application source and compile a static binary
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /app/bin/server ./main.go

# Stage 2: Minimal runtime image
FROM alpine:3.21

RUN apk add --no-cache ca-certificates tzdata && \
    adduser -D -g '' appuser

WORKDIR /app

COPY --from=builder /app/bin/server /app/server

USER appuser

EXPOSE 8080

CMD ["/app/server"]
