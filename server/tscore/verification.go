package tscore

import (
    "errors"
    "strings"
    "time"
)

type VerificationType string
type VerificationStatus string

const (
    VerificationEmail    VerificationType = "EMAIL"
    VerificationPhone    VerificationType = "PHONE"
    VerificationIdentity VerificationType = "IDENTITY"
    VerificationAddress  VerificationType = "ADDRESS"
    VerificationLiveness VerificationType = "LIVENESS"
    VerificationPhoto    VerificationType = "PROFILE_PHOTO"
)

const (
    VerificationPending  VerificationStatus = "PENDING"
    VerificationApproved VerificationStatus = "APPROVED"
    VerificationRejected VerificationStatus = "REJECTED"
)

var (
    ErrVerificationNotFound = errors.New("verification request not found")
    ErrVerificationExists   = errors.New("verification request already pending")
    ErrVerificationInvalid  = errors.New("invalid verification request")
)

type VerificationRequest struct {
    ID          string             `json:"id"`
    UserID      string             `json:"user_id"`
    Type        VerificationType   `json:"type"`
    Status      VerificationStatus `json:"status"`
    EvidenceRef string             `json:"-"`
    Note        string             `json:"note,omitempty"`
    CreatedAt   time.Time          `json:"created_at"`
    UpdatedAt   time.Time          `json:"updated_at"`
    ReviewedAt  *time.Time         `json:"reviewed_at,omitempty"`
}

type VerificationSummary struct {
    UserID string `json:"user_id"`
    Email bool `json:"email_verified"`
    Phone bool `json:"phone_verified"`
    Identity bool `json:"identity_verified"`
    Address bool `json:"address_verified"`
    Liveness bool `json:"liveness_verified"`
    ProfilePhoto bool `json:"profile_photo_verified"`
}

func NormalizeVerificationType(v VerificationType) (VerificationType, error) {
    t := VerificationType(strings.ToUpper(strings.TrimSpace(string(v))))
    switch t {
    case VerificationEmail, VerificationPhone, VerificationIdentity, VerificationAddress, VerificationLiveness, VerificationPhoto:
        return t,nil
    default:
        return "",ErrVerificationInvalid
    }
}

func IsDocumentVerification(t VerificationType) bool {
    return t == VerificationIdentity || t == VerificationAddress || t == VerificationLiveness || t == VerificationPhoto
}
