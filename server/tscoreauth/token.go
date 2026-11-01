package tscoreauth

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"time"

	"github.com/tinode/chat/server/auth"
	"github.com/tinode/chat/server/store/types"
)

type tokenLayout struct {
	Uid          uint64
	Expires      uint32
	AuthLevel    uint16
	SerialNumber uint16
	Features     uint16
}

type TokenVerifier struct {
	key    []byte
	serial uint16
	now    func() time.Time
}

func NewTokenVerifierFromBase64(keyBase64 string, serial int) (*TokenVerifier, error) {
	key, err := base64.StdEncoding.DecodeString(keyBase64)
	if err != nil { return nil, fmt.Errorf("decode token key: %w", err) }
	if len(key) < sha256.Size { return nil, errors.New("token key is too short") }
	if serial < 0 || serial > 65535 { return nil, errors.New("invalid token serial") }
	return &TokenVerifier{key:key, serial:uint16(serial), now:time.Now}, nil
}

func (v *TokenVerifier) VerifyBase64(token string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(token)
	if err != nil { return "", errors.New("malformed token") }
	return v.VerifyRaw(raw)
}

func (v *TokenVerifier) VerifyRaw(raw []byte) (string, error) {
	var tl tokenLayout
	dataSize := binary.Size(&tl)
	if len(raw) != dataSize+sha256.Size { return "", errors.New("malformed token") }
	if err := binary.Read(bytes.NewReader(raw[:dataSize]), binary.LittleEndian, &tl); err != nil { return "", errors.New("malformed token") }

	mac := hmac.New(sha256.New, v.key)
	mac.Write(raw[:dataSize])
	if !hmac.Equal(raw[dataSize:], mac.Sum(nil)) { return "", errors.New("invalid token signature") }
	if tl.SerialNumber != v.serial { return "", errors.New("token serial mismatch") }
	if auth.Level(tl.AuthLevel) < auth.LevelAuth || auth.Level(tl.AuthLevel) > auth.LevelRoot { return "", errors.New("insufficient authentication") }
	if time.Unix(int64(tl.Expires),0).UTC().Before(v.now().Add(time.Second)) { return "", errors.New("expired token") }

	uid := types.Uid(tl.Uid)
	if uid.IsZero() { return "", errors.New("invalid user id") }
	return uid.String(), nil
}
