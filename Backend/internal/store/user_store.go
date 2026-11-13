package store

import (
	"errors"
	"time"

	"gorm.io/gorm"
)

type User struct {
	ID          uint      `gorm:"primaryKey" json:"id"`
	GoogleID    string    `gorm:"uniqueIndex;not null" json:"-"`
	Email       string    `gorm:"uniqueIndex;not null" json:"email"`
	DisplayName string    `json:"display_name"`
	AvatarURL   string    `json:"avatar_url"`
	CreatedAt   time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt   time.Time `gorm:"autoUpdateTime" json:"updated_at"`
	LastLoginAt time.Time `json:"last_login_at"`
}

var AnonymousUser = &User{}

func (u *User) IsAnonymous() bool {
	return u == AnonymousUser
}

type UserStore interface {
	FindOrCreateGoogleUser(googleID, email, name, avatarURL string) (*User, error)
	GetUserByID(id uint) (*User, error)
	GetUserByEmail(email string) (*User, error)
	GetUserByGoogleID(googleID string) (*User, error)
	UpdateUser(user *User) error
}

type PostgresUserStore struct {
	db *gorm.DB
}

func NewPostgresUserStore(db *gorm.DB) *PostgresUserStore {
	return &PostgresUserStore{db: db}
}

func (s *PostgresUserStore) FindOrCreateGoogleUser(googleID, email, name, avatarURL string) (*User, error) {
	var user User
	err := s.db.Where("google_id = ?", googleID).First(&user).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			// Also check if email already exists
			err = s.db.Where("email = ?", email).First(&user).Error
			if err == nil {
				// Update existing email record with google_id
				user.GoogleID = googleID
				if name != "" {
					user.DisplayName = name
				}
				if avatarURL != "" {
					user.AvatarURL = avatarURL
				}
				user.LastLoginAt = time.Now()
				if err := s.db.Save(&user).Error; err != nil {
					return nil, err
				}
				return &user, nil
			}

			// Create brand new user
			newUser := &User{
				GoogleID:    googleID,
				Email:       email,
				DisplayName: name,
				AvatarURL:   avatarURL,
				LastLoginAt: time.Now(),
			}
			if err := s.db.Create(newUser).Error; err != nil {
				return nil, err
			}
			return newUser, nil
		}
		return nil, err
	}

	// Update existing record's last login and details if changed
	user.LastLoginAt = time.Now()
	if name != "" {
		user.DisplayName = name
	}
	if avatarURL != "" {
		user.AvatarURL = avatarURL
	}
	if err := s.db.Save(&user).Error; err != nil {
		return nil, err
	}

	return &user, nil
}

func (s *PostgresUserStore) GetUserByID(id uint) (*User, error) {
	var user User
	err := s.db.First(&user, id).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &user, nil
}

func (s *PostgresUserStore) GetUserByEmail(email string) (*User, error) {
	var user User
	err := s.db.Where("email = ?", email).First(&user).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &user, nil
}

func (s *PostgresUserStore) GetUserByGoogleID(googleID string) (*User, error) {
	var user User
	err := s.db.Where("google_id = ?", googleID).First(&user).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &user, nil
}

func (s *PostgresUserStore) UpdateUser(user *User) error {
	return s.db.Save(user).Error
}
