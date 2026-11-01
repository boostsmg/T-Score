package tscoreauth

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"testing"
	"time"

	"github.com/tinode/chat/server/auth"
	"github.com/tinode/chat/server/store/types"
)

func makeToken(t *testing.T, key []byte, uid types.Uid, serial uint16, expires time.Time) string {
	t.Helper()
	tl := tokenLayout{Uid:uint64(uid),Expires:uint32(expires.Unix()),AuthLevel:uint16(auth.LevelAuth),SerialNumber:serial}
	buf := new(bytes.Buffer)
	if err := binary.Write(buf,binary.LittleEndian,&tl); err != nil { t.Fatal(err) }
	mac := hmac.New(sha256.New,key); mac.Write(buf.Bytes()); buf.Write(mac.Sum(nil))
	return base64.StdEncoding.EncodeToString(buf.Bytes())
}

func TestVerifyToken(t *testing.T) {
	key := bytes.Repeat([]byte{7},32)
	v,err := NewTokenVerifierFromBase64(base64.StdEncoding.EncodeToString(key),4)
	if err != nil { t.Fatal(err) }
	now := time.Unix(1800000000,0).UTC(); v.now = func() time.Time { return now }
	uid := types.Uid(123456789)
	token := makeToken(t,key,uid,4,now.Add(time.Hour))
	got,err := v.VerifyBase64(token)
	if err != nil { t.Fatal(err) }
	if got != uid.UserId() { t.Fatalf("got %s want %s",got,uid.UserId()) }
}

func TestExpiredAndTamperedTokenRejected(t *testing.T) {
	key := bytes.Repeat([]byte{9},32)
	v,err := NewTokenVerifierFromBase64(base64.StdEncoding.EncodeToString(key),1)
	if err != nil { t.Fatal(err) }
	now := time.Unix(1800000000,0).UTC(); v.now = func() time.Time { return now }
	uid := types.Uid(42)
	if _,err := v.VerifyBase64(makeToken(t,key,uid,1,now.Add(-time.Minute))); err == nil { t.Fatal("expected expired token error") }
	tokenRaw,_ := base64.StdEncoding.DecodeString(makeToken(t,key,uid,1,now.Add(time.Hour)))
	tokenRaw[len(tokenRaw)-1] ^= 0xff
	if _,err := v.VerifyBase64(base64.StdEncoding.EncodeToString(tokenRaw)); err == nil { t.Fatal("expected signature error") }
}
