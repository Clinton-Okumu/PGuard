package auth

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"

	"google.golang.org/api/idtoken"
)

var (
	ErrInvalidGoogleToken = errors.New("invalid google token")
)

type GooglePayload struct {
	GoogleID string
	Email    string
	Name     string
	Picture  string
}

type GoogleVerifier struct {
	allowedClientIDs []string
	devMockAuth      bool
}

func NewGoogleVerifier() *GoogleVerifier {
	rawClientIDs := os.Getenv("GOOGLE_CLIENT_IDS")
	var clientIDs []string
	if rawClientIDs != "" {
		for _, id := range strings.Split(rawClientIDs, ",") {
			trimmed := strings.TrimSpace(id)
			if trimmed != "" {
				clientIDs = append(clientIDs, trimmed)
			}
		}
	}

	devMockAuth := strings.EqualFold(os.Getenv("DEV_MOCK_AUTH"), "true")

	return &GoogleVerifier{
		allowedClientIDs: clientIDs,
		devMockAuth:      devMockAuth,
	}
}

func (v *GoogleVerifier) VerifyToken(ctx context.Context, rawToken string) (*GooglePayload, error) {
	if rawToken == "" {
		return nil, errors.New("token cannot be empty")
	}

	// Development bypass: allow mock tokens when DEV_MOCK_AUTH=true
	if v.devMockAuth && strings.HasPrefix(rawToken, "mock") {
		email := "dev.user@pguard.local"
		name := "Dev Tester"

		parts := strings.Split(rawToken, ":")
		if len(parts) == 2 && parts[1] != "" {
			email = parts[1]
			name = strings.Split(email, "@")[0]
		} else if len(parts) >= 3 {
			name = parts[1]
			email = parts[2]
		}

		googleID := "mock_google_" + email

		return &GooglePayload{
			GoogleID: googleID,
			Email:    email,
			Name:     name,
			Picture:  "https://ui-avatars.com/api/?name=" + name,
		}, nil
	}

	var payload *idtoken.Payload
	var err error

	if len(v.allowedClientIDs) == 0 {
		payload, err = idtoken.Validate(ctx, rawToken, "")
		if err != nil {
			return nil, fmt.Errorf("%w: %v", ErrInvalidGoogleToken, err)
		}
	} else {
		var valid bool
		for _, clientID := range v.allowedClientIDs {
			payload, err = idtoken.Validate(ctx, rawToken, clientID)
			if err == nil {
				valid = true
				break
			}
		}
		if !valid {
			return nil, fmt.Errorf("%w: token audience does not match allowed client IDs", ErrInvalidGoogleToken)
		}
	}

	email, _ := payload.Claims["email"].(string)
	name, _ := payload.Claims["name"].(string)
	picture, _ := payload.Claims["picture"].(string)

	if payload.Subject == "" || email == "" {
		return nil, errors.New("google token missing subject or email")
	}

	return &GooglePayload{
		GoogleID: payload.Subject,
		Email:    email,
		Name:     name,
		Picture:  picture,
	}, nil
}
