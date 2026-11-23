package store

import (
	"crypto/rand"
	"crypto/subtle"
	"errors"
	"fmt"
	"math/big"
	"net/mail"
	"strings"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

var (
	ErrPartnerNotFound            = errors.New("accountability partner not found")
	ErrInvalidEmail               = errors.New("invalid partner email address")
	ErrSelfInvite                 = errors.New("cannot invite yourself as an accountability partner")
	ErrInvalidInviteCode          = errors.New("invalid or expired invite code")
	ErrInviteNotPending           = errors.New("invite has already been used or disconnected")
	ErrInviteExpired              = errors.New("invite code has expired")
	ErrInvalidEventType           = errors.New("invalid security event type")
	ErrInvalidUnlockCode          = errors.New("invalid or expired approval code")
	ErrUnlockCodeAttemptsExceeded = errors.New("too many invalid approval code attempts; request a new code")
	ErrUnlockCodeFeatureMismatch  = errors.New("approval code was issued for a different action")
	ErrTimeDelayNotFound          = errors.New("no active time delay unlock found")
)

const (
	PartnerStatusPending      = "pending"
	PartnerStatusAccepted     = "accepted"
	PartnerStatusDisconnected = "disconnected"

	EventSOSTriggered       = "sos_triggered"
	EventProtectionDisabled = "protection_disabled"
	EventPermissionRevoked  = "permission_revoked"
	EventUninstallAttempt   = "uninstall_attempt"
)

func IsValidEventType(t string) bool {
	switch strings.ToLower(strings.TrimSpace(t)) {
	case EventSOSTriggered, EventProtectionDisabled, EventPermissionRevoked, EventUninstallAttempt:
		return true
	default:
		return false
	}
}

// IsValidEmail validates an email address using net/mail and requires a non-empty local part
// plus a dot-containing domain without leading/trailing dots or whitespace.
func IsValidEmail(raw string) bool {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" || strings.ContainsAny(trimmed, " \t\r\n") {
		return false
	}
	parsed, err := mail.ParseAddress(trimmed)
	if err != nil || !strings.EqualFold(parsed.Address, trimmed) {
		return false
	}
	atIdx := strings.LastIndex(parsed.Address, "@")
	if atIdx <= 0 || atIdx+1 >= len(parsed.Address) {
		return false
	}
	domain := parsed.Address[atIdx+1:]
	if !strings.Contains(domain, ".") || strings.HasPrefix(domain, ".") || strings.HasSuffix(domain, ".") {
		return false
	}
	return true
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
	InviteCode    string    `gorm:"size:16;uniqueIndex;not null" json:"-"`
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

type UnlockCode struct {
	ID          uint      `gorm:"primaryKey" json:"id"`
	UserID      uint      `gorm:"index;not null" json:"user_id"`
	FeatureName string    `gorm:"size:120;not null" json:"feature_name"`
	CodeHash    string    `gorm:"size:64;not null" json:"-"`
	Attempts    int       `gorm:"default:0;not null" json:"attempts"`
	MaxAttempts int       `gorm:"default:5;not null" json:"max_attempts"`
	Used        bool      `gorm:"default:false;not null" json:"used"`
	ExpiresAt   time.Time `gorm:"not null" json:"expires_at"`
	CreatedAt   time.Time `gorm:"autoCreateTime" json:"created_at"`
	User        User      `gorm:"foreignKey:UserID" json:"-"`
}

type TimeDelayUnlock struct {
	ID          uint      `gorm:"primaryKey" json:"id"`
	UserID      uint      `gorm:"uniqueIndex;not null" json:"user_id"`
	TargetKey   string    `gorm:"size:160;not null" json:"target_key"`
	TargetTitle string    `gorm:"size:160;not null" json:"target_title"`
	UnlockAt    time.Time `gorm:"not null" json:"unlock_at"`
	CreatedAt   time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt   time.Time `gorm:"autoUpdateTime" json:"updated_at"`
	User        User      `gorm:"foreignKey:UserID" json:"-"`
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

	CreateUnlockCode(userID uint, featureName, codeHash string, expiresAt time.Time) (*UnlockCode, error)
	VerifyAndConsumeUnlockCode(userID uint, codeHash, expectedFeature string) (*UnlockCode, error)

	UpsertTimeDelay(userID uint, targetKey, targetTitle string, unlockAt time.Time) (*TimeDelayUnlock, error)
	GetTimeDelay(userID uint) (*TimeDelayUnlock, error)
	ClearTimeDelay(userID uint) error

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
	if !IsValidEmail(email) {
		return nil, ErrInvalidEmail
	}

	if AreEmailsEquivalent(email, userEmail) {
		return nil, ErrSelfInvite
	}

	// Disconnect any other pending/accepted partner records for this user so orphaned rows cannot remain
	if err := s.db.Model(&AccountabilityPartner{}).
		Where("user_id = ? AND partner_email != ? AND status != ?", userID, email, PartnerStatusDisconnected).
		Update("status", PartnerStatusDisconnected).Error; err != nil {
		return nil, err
	}

	// Check if already invited or linked
	var existing AccountabilityPartner
	err := s.db.Where("user_id = ? AND partner_email = ?", userID, email).First(&existing).Error
	if err == nil {
		trimmedName := strings.TrimSpace(partnerName)
		if existing.Status == PartnerStatusDisconnected {
			existing.Status = PartnerStatusPending
			existing.InviteCode = GenerateInviteCode()
			existing.PartnerName = trimmedName
			existing.UpdatedAt = time.Now()
			if err := s.db.Save(&existing).Error; err != nil {
				return nil, err
			}
			return &existing, nil
		}
		if existing.Status == PartnerStatusPending {
			existing.UpdatedAt = time.Now()
			if trimmedName != "" {
				existing.PartnerName = trimmedName
			}
			if err := s.db.Save(&existing).Error; err != nil {
				return nil, err
			}
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
	err := s.db.Preload("User").
		Where("invite_code = ?", strings.ToUpper(strings.TrimSpace(code))).
		First(&partner).Error
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

	if partner.Status != PartnerStatusPending {
		return nil, ErrInviteNotPending
	}
	if time.Since(partner.UpdatedAt) > 7*24*time.Hour {
		return nil, ErrInviteExpired
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

func (s *PostgresAccountabilityStore) CreateUnlockCode(userID uint, featureName, codeHash string, expiresAt time.Time) (*UnlockCode, error) {
	var created *UnlockCode
	err := s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&UnlockCode{}).
			Where("user_id = ? AND used = false", userID).
			Update("used", true).Error; err != nil {
			return err
		}
		rec := &UnlockCode{
			UserID:      userID,
			FeatureName: strings.TrimSpace(featureName),
			CodeHash:    codeHash,
			Attempts:    0,
			MaxAttempts: 5,
			Used:        false,
			ExpiresAt:   expiresAt,
		}
		if err := tx.Create(rec).Error; err != nil {
			return err
		}
		created = rec
		return nil
	})
	if err != nil {
		return nil, err
	}
	return created, nil
}

func (s *PostgresAccountabilityStore) VerifyAndConsumeUnlockCode(userID uint, codeHash, expectedFeature string) (*UnlockCode, error) {
	var verified *UnlockCode
	var domainErr error

	txErr := s.db.Transaction(func(tx *gorm.DB) error {
		var rec UnlockCode
		err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
			Where("user_id = ? AND used = false", userID).
			Order("created_at DESC").
			First(&rec).Error
		if err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				domainErr = ErrInvalidUnlockCode
				return nil
			}
			return err
		}

		if rec.ExpiresAt.Before(time.Now()) {
			rec.Used = true
			if err := tx.Save(&rec).Error; err != nil {
				return err
			}
			domainErr = ErrInvalidUnlockCode
			return nil
		}

		if rec.Attempts >= rec.MaxAttempts {
			rec.Used = true
			if err := tx.Save(&rec).Error; err != nil {
				return err
			}
			domainErr = ErrUnlockCodeAttemptsExceeded
			return nil
		}

		expected := strings.TrimSpace(expectedFeature)
		if expected != "" && !strings.EqualFold(strings.TrimSpace(rec.FeatureName), expected) {
			rec.Attempts++
			if rec.Attempts >= rec.MaxAttempts {
				rec.Used = true
			}
			if err := tx.Save(&rec).Error; err != nil {
				return err
			}
			domainErr = ErrUnlockCodeFeatureMismatch
			return nil
		}

		if subtle.ConstantTimeCompare([]byte(rec.CodeHash), []byte(codeHash)) != 1 {
			rec.Attempts++
			if rec.Attempts >= rec.MaxAttempts {
				rec.Used = true
				domainErr = ErrUnlockCodeAttemptsExceeded
			} else {
				domainErr = ErrInvalidUnlockCode
			}
			if err := tx.Save(&rec).Error; err != nil {
				return err
			}
			return nil
		}

		rec.Used = true
		if err := tx.Save(&rec).Error; err != nil {
			return err
		}
		verified = &rec
		return nil
	})

	if txErr != nil {
		return nil, txErr
	}
	if domainErr != nil {
		return nil, domainErr
	}
	return verified, nil
}

func (s *PostgresAccountabilityStore) UpsertTimeDelay(userID uint, targetKey, targetTitle string, unlockAt time.Time) (*TimeDelayUnlock, error) {
	var existing TimeDelayUnlock
	err := s.db.Where("user_id = ?", userID).First(&existing).Error
	if err == nil {
		existing.TargetKey = strings.TrimSpace(targetKey)
		existing.TargetTitle = strings.TrimSpace(targetTitle)
		existing.UnlockAt = unlockAt.UTC()
		if err := s.db.Save(&existing).Error; err != nil {
			return nil, err
		}
		return &existing, nil
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, err
	}

	rec := &TimeDelayUnlock{
		UserID:      userID,
		TargetKey:   strings.TrimSpace(targetKey),
		TargetTitle: strings.TrimSpace(targetTitle),
		UnlockAt:    unlockAt.UTC(),
	}
	if err := s.db.Create(rec).Error; err != nil {
		return nil, err
	}
	return rec, nil
}

func (s *PostgresAccountabilityStore) GetTimeDelay(userID uint) (*TimeDelayUnlock, error) {
	var rec TimeDelayUnlock
	if err := s.db.Where("user_id = ?", userID).First(&rec).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, ErrTimeDelayNotFound
		}
		return nil, err
	}
	return &rec, nil
}

func (s *PostgresAccountabilityStore) ClearTimeDelay(userID uint) error {
	result := s.db.Where("user_id = ?", userID).Delete(&TimeDelayUnlock{})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return ErrTimeDelayNotFound
	}
	return nil
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
