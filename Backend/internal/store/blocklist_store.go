package store

import (
	"errors"
	"strings"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

var (
	ErrInvalidBlockType = errors.New("invalid blocklist item type, must be 'app', 'website', or 'keyword'")
	ErrEmptyBlockValue  = errors.New("blocklist item value cannot be empty")
	ErrBlockItemNotFound = errors.New("blocklist item not found")
)

const (
	BlockTypeApp     = "app"
	BlockTypeWebsite = "website"
	BlockTypeKeyword = "keyword"
)

func IsValidBlockType(t string) bool {
	lower := strings.ToLower(strings.TrimSpace(t))
	return lower == BlockTypeApp || lower == BlockTypeWebsite || lower == BlockTypeKeyword
}

type BlocklistItem struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	UserID    uint      `gorm:"not null;index:idx_user_type_value,unique" json:"user_id"`
	Type      string    `gorm:"size:20;not null;index:idx_user_type_value,unique" json:"type"`
	Value     string    `gorm:"size:255;not null;index:idx_user_type_value,unique" json:"value"`
	Label     string    `gorm:"size:100" json:"label"`
	IsEnabled bool      `gorm:"default:true;not null" json:"is_enabled"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt time.Time `gorm:"autoUpdateTime" json:"updated_at"`
	User      User      `gorm:"foreignKey:UserID" json:"-"`
}

type BlocklistStore interface {
	GetByUserID(userID uint, itemType string) ([]BlocklistItem, error)
	GetByID(userID uint, id uint) (*BlocklistItem, error)
	Create(item *BlocklistItem) (*BlocklistItem, error)
	BulkCreate(userID uint, items []BlocklistItem) ([]BlocklistItem, error)
	ToggleEnabled(userID uint, id uint, isEnabled bool) (*BlocklistItem, error)
	Delete(userID uint, id uint) error
}

type PostgresBlocklistStore struct {
	db *gorm.DB
}

func NewPostgresBlocklistStore(db *gorm.DB) *PostgresBlocklistStore {
	return &PostgresBlocklistStore{db: db}
}

func (s *PostgresBlocklistStore) GetByUserID(userID uint, itemType string) ([]BlocklistItem, error) {
	var items []BlocklistItem
	query := s.db.Where("user_id = ?", userID)

	if itemType != "" {
		query = query.Where("type = ?", strings.ToLower(strings.TrimSpace(itemType)))
	}

	err := query.Order("created_at DESC").Find(&items).Error
	if err != nil {
		return nil, err
	}
	return items, nil
}

func (s *PostgresBlocklistStore) GetByID(userID uint, id uint) (*BlocklistItem, error) {
	var item BlocklistItem
	err := s.db.Where("id = ? AND user_id = ?", id, userID).First(&item).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, ErrBlockItemNotFound
		}
		return nil, err
	}
	return &item, nil
}

func (s *PostgresBlocklistStore) Create(item *BlocklistItem) (*BlocklistItem, error) {
	// If rule already exists for user (same type and value), update enabled and label instead of erroring
	err := s.db.Clauses(clause.OnConflict{
		Columns: []clause.Column{
			{Name: "user_id"},
			{Name: "type"},
			{Name: "value"},
		},
		DoUpdates: clause.AssignmentColumns([]string{"label", "is_enabled", "updated_at"}),
	}).Create(item).Error

	if err != nil {
		return nil, err
	}
	return item, nil
}

func (s *PostgresBlocklistStore) BulkCreate(userID uint, items []BlocklistItem) ([]BlocklistItem, error) {
	if len(items) == 0 {
		return []BlocklistItem{}, nil
	}

	for i := range items {
		items[i].UserID = userID
	}

	err := s.db.Clauses(clause.OnConflict{
		Columns: []clause.Column{
			{Name: "user_id"},
			{Name: "type"},
			{Name: "value"},
		},
		DoUpdates: clause.AssignmentColumns([]string{"label", "is_enabled", "updated_at"}),
	}).Create(&items).Error

	if err != nil {
		return nil, err
	}
	return items, nil
}

func (s *PostgresBlocklistStore) ToggleEnabled(userID uint, id uint, isEnabled bool) (*BlocklistItem, error) {
	item, err := s.GetByID(userID, id)
	if err != nil {
		return nil, err
	}

	item.IsEnabled = isEnabled
	if err := s.db.Save(item).Error; err != nil {
		return nil, err
	}
	return item, nil
}

func (s *PostgresBlocklistStore) Delete(userID uint, id uint) error {
	result := s.db.Where("id = ? AND user_id = ?", id, userID).Delete(&BlocklistItem{})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected == 0 {
		return ErrBlockItemNotFound
	}
	return nil
}
