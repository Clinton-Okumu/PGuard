package store

import (
	"crypto/rand"
	"errors"
	"fmt"
	"math/big"
	"strings"
	"time"

	"gorm.io/gorm"
)

var (
	ErrPartnerNotFound   = errors.New("accountability partner not found")
	ErrInvalidEmail      = errors.New("invalid partner email address")
	ErrSelfInvite        = errors.New("cannot invite yourself as an accountability partner")
	ErrInvalidInviteCode = errors.New("invalid or expired invite code")
	ErrInvalidEventType  = errors.New("invalid security event type")
)

const (
	PartnerStatusPending      = "pending"
	PartnerStatusAccepted     = "accepted"
	PartnerStatusDisconnected = "disconnected"

	EventSOSTriggered         = "sos_triggered"
	EventProtectionDisabled   = "protection_disabled"
	EventPermissionRevoked    = "permission_revoked"
	EventUninstallAttempt     = "uninstall_attempt"
)

func IsValidEventType(t string) bool {
	switch strings.ToLower(strings.TrimSpace(t)) {
	case EventSOSTriggered, EventProtectionDisabled, EventPermissionRevoked, EventUninstallAttempt:
		return true
	default:
		return false
	}
}

// NormalizeEmail normalizes an email for comparison:
// trims, converts to lowercase, strips plus-tag addressing, and ignores dots for Gmail.
func NormalizeEmail(email string) string {
	email = strings.ToLower(strings.TrimSpace(email))
	parts := strings.Split(email, "@")
	if len(parts) != 2 {
		return email
	}
	user, domain := parts[0], parts[1]
	if idx := strings.Index(user, "+"); idx != -1 {
		user = user[:idx]
	}
	if domain == "gmail.com" || domain == "googlemail.com" {
		user = strings.ReplaceAll(user, ".", "")
	}
	return user + "@" + domain
}

// AreEmailsEquivalent returns true if two emails resolve to the same account or are equal.
func AreEmailsEquivalent(email1, email2 string) bool {
	e1 := strings.TrimSpace(email1)
	e2 := strings.TrimSpace(email2)
	if e1 == "" || e2 == "" {
		return false
	}
	if strings.EqualFold(e1, e2) {
		return true
	}
	return NormalizeEmail(e1) == NormalizeEmail(e2)
}

type AccountabilityPartner struct {
	ID            uint      `gorm:"primaryKey" json:"id"`
	UserID        uint      `gorm:"not null;index:idx_user_partner_email,unique" json:"user_id"`
	PartnerEmail  string    `gorm:"size:255;not null;index:idx_user_partner_email,unique" json:"partner_email"`
	PartnerName   string    `gorm:"size:100" json:"partner_name"`
	PartnerUserID *uint     `gorm:"index" json:"partner_user_id,omitempty"`
	InviteCode    string    `gorm:"size:16;uniqueIndex;not null" json:"invite_code"`
	Status        string    `gorm:"size:20;default:'pending';not null" json:"status"`
	CreatedAt     time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt     time.Time `gorm:"autoUpdateTime" json:"updated_at"`
	User          User      `gorm:"foreignKey:UserID" json:"-"`
}

type SecurityAlert struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	UserID    uint      `gorm:"index;not null" json:"user_id"`
	EventType string    `gorm:"size:50;not null" json:"event_type"`
	Message   string    `gorm:"size:500;not null" json:"message"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
	User      User      `gorm:"foreignKey:UserID" json:"-"`
}

func GenerateInviteCode() string {
	const charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	b := make([]byte, 6)
	for i := range b {
		num, _ := rand.Int(rand.Reader, big.NewInt(int64(len(charset))))
		b[i] = charset[num.Int64()]
	}
	return fmt.Sprintf("PG-%s", string(b))
}

type AccountabilityStore interface {
	CreateInvite(userID uint, userEmail, partnerEmail, partnerName string) (*AccountabilityPartner, error)
	GetPartnerByUserID(userID uint) (*AccountabilityPartner, error)
	GetInviteByCode(code string) (*AccountabilityPartner, error)
	AcceptInvite(code string, partnerUserID *uint) (*AccountabilityPartner, error)
	DisconnectPartner(userID uint) error

	RecordSecurityEvent(userID uint, eventType, message string) (*SecurityAlert, error)
	GetRecentEvents(userID uint, limit int) ([]SecurityAlert, error)
}

type PostgresAccountabilityStore struct {
	db *gorm.DB
}

func NewPostgresAccountabilityStore(db *gorm.DB) *PostgresAccountabilityStore {
	return &PostgresAccountabilityStore{db: db}
}

func (s *PostgresAccountabilityStore) CreateInvite(userID uint, userEmail, partnerEmail, partnerName string) (*AccountabilityPartner, error) {
	email := strings.ToLower(strings.TrimSpace(partnerEmail))
	if email == "" || !strings.Contains(email, "@") {
		return nil, ErrInvalidEmail
	}

	if AreEmailsEquivalent(email, userEmail) {
		return nil, ErrSelfInvite
	}

	// Check if already invited or linked
	var existing AccountabilityPartner
	err := s.db.Where("user_id = ? AND partner_email = ?", userID, email).First(&existing).Error
	if err == nil {
		if existing.Status == PartnerStatusDisconnected {
			existing.Status = PartnerStatusPending
			existing.InviteCode = GenerateInviteCode()
			existing.PartnerName = strings.TrimSpace(partnerName)
			if err := s.db.Save(&existing).Error; err != nil {
				return nil, err
			}
			return &existing, nil
		}
		return &existing, nil
	}

	partner := &AccountabilityPartner{
		UserID:       userID,
		PartnerEmail: email,
		PartnerName:  strings.TrimSpace(partnerName),
		InviteCode:   GenerateInviteCode(),
		Status:       PartnerStatusPending,
	}

	if err := s.db.Create(partner).Error; err != nil {
		return nil, err
	}
	return partner, nil
}

func (s *PostgresAccountabilityStore) GetPartnerByUserID(userID uint) (*AccountabilityPartner, error) {
	var partner AccountabilityPartner
	err := s.db.Where("user_id = ? AND status != ?", userID, PartnerStatusDisconnected).
		Order("created_at DESC").
		First(&partner).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, ErrPartnerNotFound
		}
		return nil, err
	}
	return &partner, nil
}

func (s *PostgresAccountabilityStore) GetInviteByCode(code string) (*AccountabilityPartner, error) {
	var partner AccountabilityPartner
	err := s.db.Where("invite_code = ?", strings.ToUpper(strings.TrimSpace(code))).First(&partner).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, ErrInvalidInviteCode
		}
		return nil, err
	}
	return &partner, nil
}

func (s *PostgresAccountabilityStore) AcceptInvite(code string, partnerUserID *uint) (*AccountabilityPartner, error) {
	partner, err := s.GetInviteByCode(code)
	if err != nil {
		return nil, err
	}

	if partnerUserID != nil && *partnerUserID == partner.UserID {
		return nil, ErrSelfInvite
	}

	partner.Status = PartnerStatusAccepted
	if partnerUserID != nil {
		partner.PartnerUserID = partnerUserID
	}

	if err := s.db.Save(partner).Error; err != nil {
		return nil, err
	}
	return partner, nil
}

func (s *PostgresAccountabilityStore) DisconnectPartner(userID uint) error {
	partner, err := s.GetPartnerByUserID(userID)
	if err != nil {
		return err
	}

	partner.Status = PartnerStatusDisconnected
	return s.db.Save(partner).Error
}

func (s *PostgresAccountabilityStore) RecordSecurityEvent(userID uint, eventType, message string) (*SecurityAlert, error) {
	if !IsValidEventType(eventType) {
		return nil, ErrInvalidEventType
	}

	alert := &SecurityAlert{
		UserID:    userID,
		EventType: strings.ToLower(strings.TrimSpace(eventType)),
		Message:   strings.TrimSpace(message),
	}

	if err := s.db.Create(alert).Error; err != nil {
		return nil, err
	}
	return alert, nil
}

func (s *PostgresAccountabilityStore) GetRecentEvents(userID uint, limit int) ([]SecurityAlert, error) {
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	var alerts []SecurityAlert
	err := s.db.Where("user_id = ?", userID).
		Order("created_at DESC").
		Limit(limit).
		Find(&alerts).Error
	if err != nil {
		return nil, err
	}
	return alerts, nil
}
