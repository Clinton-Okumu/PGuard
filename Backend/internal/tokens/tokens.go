package tokens

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"strconv"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

var (
	ErrInvalidToken = errors.New("invalid or expired token")
)

type UserClaims struct {
	UserID uint   `json:"user_id"`
	Email  string `json:"email"`
	jwt.RegisteredClaims
}

type TokenService struct {
	secret                 []byte
	accessDurationMinutes  int
	refreshDurationDays    int
}

func NewTokenService() *TokenService {
	secret := os.Getenv("JWT_SECRET")
	if secret == "" {
		secret = "pguard-dev-jwt-super-secure-secret-key-32-chars"
	}

	accessMinutes, err := strconv.Atoi(os.Getenv("JWT_ACCESS_DURATION_MINUTES"))
	if err != nil || accessMinutes <= 0 {
		accessMinutes = 30
	}

	refreshDays, err := strconv.Atoi(os.Getenv("JWT_REFRESH_DURATION_DAYS"))
	if err != nil || refreshDays <= 0 {
		refreshDays = 60
	}

	return &TokenService{
		secret:                []byte(secret),
		accessDurationMinutes: accessMinutes,
		refreshDurationDays:   refreshDays,
	}
}

func (s *TokenService) GenerateAccessToken(userID uint, email string) (string, int64, error) {
	duration := time.Duration(s.accessDurationMinutes) * time.Minute
	expiresAt := time.Now().Add(duration)

	claims := &UserClaims{
		UserID: userID,
		Email:  email,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   strconv.FormatUint(uint64(userID), 10),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			NotBefore: jwt.NewNumericDate(time.Now()),
			Issuer:    "pguard-backend",
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString(s.secret)
	if err != nil {
		return "", 0, fmt.Errorf("failed to sign access token: %w", err)
	}

	return tokenString, int64(duration.Seconds()), nil
}

func (s *TokenService) ValidateAccessToken(tokenString string) (*UserClaims, error) {
	token, err := jwt.ParseWithClaims(tokenString, &UserClaims{}, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return s.secret, nil
	})

	if err != nil {
		return nil, ErrInvalidToken
	}

	claims, ok := token.Claims.(*UserClaims)
	if !ok || !token.Valid {
		return nil, ErrInvalidToken
	}

	return claims, nil
}

func (s *TokenService) GenerateRefreshToken() (plainToken string, tokenHash string, expiresAt time.Time, err error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", "", time.Time{}, err
	}

	plainToken = base64.RawURLEncoding.EncodeToString(b)
	tokenHash = HashToken(plainToken)
	expiresAt = time.Now().Add(time.Duration(s.refreshDurationDays) * 24 * time.Hour)

	return plainToken, tokenHash, expiresAt, nil
}

func HashToken(token string) string {
	hash := sha256.Sum256([]byte(token))
	return hex.EncodeToString(hash[:])
}
