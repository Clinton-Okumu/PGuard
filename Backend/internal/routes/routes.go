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

		// Public Partner One-Click Web Acceptance (No App or Login required)
		r.Get("/accountability/accept-web", app.AccountabilityHandler.HandleAcceptInviteWeb)

		// Protected endpoints
		r.Group(func(r chi.Router) {
			r.Use(app.Middleware.Authenticate)
			r.Get("/me", app.Middleware.RequireUser(app.Me))

			// Settings endpoints
			r.Get("/settings", app.Middleware.RequireUser(app.SettingsHandler.HandleGetSettings))
			r.Put("/settings", app.Middleware.RequireUser(app.SettingsHandler.HandleUpdateSettings))

			// Blocklist endpoints
			r.Get("/blocklist", app.Middleware.RequireUser(app.BlocklistHandler.HandleGetBlocklist))
			r.Post("/blocklist", app.Middleware.RequireUser(app.BlocklistHandler.HandleCreateBlocklistItem))
			r.Patch("/blocklist/{id}", app.Middleware.RequireUser(app.BlocklistHandler.HandleToggleBlocklistItem))
			r.Delete("/blocklist/{id}", app.Middleware.RequireUser(app.BlocklistHandler.HandleDeleteBlocklistItem))

			// Streak & Recovery endpoints
			r.Get("/streak", app.Middleware.RequireUser(app.HabitsHandler.HandleGetStreak))
			r.Post("/streak/reset", app.Middleware.RequireUser(app.HabitsHandler.HandleResetStreak))
			r.Get("/streak/history", app.Middleware.RequireUser(app.HabitsHandler.HandleGetRelapseHistory))

			// Habits & Daily Check-in endpoints
			r.Get("/habits", app.Middleware.RequireUser(app.HabitsHandler.HandleGetHabits))
			r.Post("/habits", app.Middleware.RequireUser(app.HabitsHandler.HandleCreateHabit))
			r.Post("/habits/{id}/toggle", app.Middleware.RequireUser(app.HabitsHandler.HandleToggleHabit))
			r.Delete("/habits/{id}", app.Middleware.RequireUser(app.HabitsHandler.HandleDeleteHabit))

			// Accountability Partner & Security Alert endpoints
			r.Get("/accountability/partner", app.Middleware.RequireUser(app.AccountabilityHandler.HandleGetPartner))
			r.Post("/accountability/invite", app.Middleware.RequireUser(app.AccountabilityHandler.HandleCreateInvite))
			r.Post("/accountability/accept", app.Middleware.RequireUser(app.AccountabilityHandler.HandleAcceptInvite))
			r.Delete("/accountability/partner", app.Middleware.RequireUser(app.AccountabilityHandler.HandleDisconnectPartner))
			r.Post("/accountability/event", app.Middleware.RequireUser(app.AccountabilityHandler.HandleRecordEvent))
			r.Get("/accountability/events", app.Middleware.RequireUser(app.AccountabilityHandler.HandleGetEvents))
			r.Post("/accountability/request-unlock", app.Middleware.RequireUser(app.AccountabilityHandler.HandleRequestUnlockCode))
		})
	})

	return r
}
