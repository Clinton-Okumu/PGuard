package main

import (
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"time"

	"Backend/internal/app"
	"Backend/internal/routes"

	"github.com/joho/godotenv"
)

func localIPv4() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return ""
	}
	for _, addr := range addrs {
		ipNet, ok := addr.(*net.IPNet)
		if !ok {
			continue
		}
		ip := ipNet.IP
		if ip == nil || ip.IsLoopback() {
			continue
		}
		ip4 := ip.To4()
		if ip4 == nil {
			continue
		}
		return ip4.String()
	}
	return ""
}

func main() {
	_ = godotenv.Load()

	// 1. Read PORT from env first
	port := os.Getenv("PORT")
	if port == "" {
		flagPort := flag.Int("port", 8080, "PGuard backend server port")
		flag.Parse()
		port = fmt.Sprintf("%d", *flagPort)
	}

	host := os.Getenv("HOST")
	if host == "" {
		host = "0.0.0.0"
	}

	application, err := app.NewApplication()
	if err != nil {
		panic(err)
	}
	defer application.DB.Close()

	router := routes.SetUpRoutes(application)

	server := &http.Server{
		Addr:         host + ":" + port,
		Handler:      router,
		IdleTimeout:  time.Minute,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
	}

	application.Logger.Printf(
		"🛡️ PGuard Backend listening on %s:%s",
		host,
		port,
	)

	if ip := localIPv4(); ip != "" {
		application.Logger.Printf("LAN URL: http://%s:%s", ip, port)
		application.Logger.Printf("API URL: http://%s:%s/api/v1", ip, port)
	}

	err = server.ListenAndServe()
	if err != nil && err != http.ErrServerClosed {
		application.Logger.Fatal(err)
	}
}
