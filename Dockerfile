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

COPY --from=builder /app/bin/server /app/server

USER appuser

EXPOSE 8080

CMD ["/app/server"]
