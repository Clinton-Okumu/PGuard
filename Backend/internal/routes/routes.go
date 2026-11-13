package routes

import (
	"Backend/internal/app"

	"github.com/go-chi/chi/v5"
	chimiddleware "github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
)

func SetUpRoutes(app *app.Application) *chi.Mux {
	r := chi.NewRouter()

	r.Use(chimiddleware.RequestID)
	r.Use(chimiddleware.Logger)
	r.Use(chimiddleware.Recoverer)

	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	r.Get("/", app.Welcome)
	r.Get("/health", app.HealthChecker)

	r.Route("/api/v1", func(r chi.Router) {
		r.Post("/auth/google", app.AuthHandler.HandleGoogleAuth)
		r.Post("/auth/refresh", app.AuthHandler.HandleRefreshToken)
		r.Post("/auth/logout", app.AuthHandler.HandleLogout)

		// Protected endpoints
		r.Group(func(r chi.Router) {
			r.Use(app.Middleware.Authenticate)
			r.Get("/me", app.Middleware.RequireUser(app.Me))
		})
	})

	return r
}
