package app

import (
	"net/http"

	"Backend/internal/middleware"
	"Backend/internal/utils"
)

func (a *Application) Me(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{"user": user})
}
