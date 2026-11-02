package tscore

import (
    "crypto/aes"
    "crypto/cipher"
    "crypto/rand"
    "encoding/base64"
    "errors"
    "fmt"
    "io"
    "os"
    "path/filepath"
    "strings"
)

const MaxEvidenceBytes int64 = 8 << 20

var ErrEvidenceInvalid = errors.New("invalid verification evidence")

type EvidenceMeta struct {
    Ref         string
    ContentType string
    Size        int64
}

type EvidenceStore struct {
    dir string
    aead cipher.AEAD
}

func NewEvidenceStore(dir, keyBase64 string) (*EvidenceStore, error) {
    if strings.TrimSpace(dir) == "" { return nil, errors.New("evidence directory is required") }
    key, err := base64.StdEncoding.DecodeString(strings.TrimSpace(keyBase64))
    if err != nil || len(key) != 32 { return nil, errors.New("evidence key must be base64 encoded 32 bytes") }
    block, err := aes.NewCipher(key); if err != nil { return nil, err }
    aead, err := cipher.NewGCM(block); if err != nil { return nil, err }
    if err := os.MkdirAll(dir, 0700); err != nil { return nil, err }
    return &EvidenceStore{dir:dir,aead:aead},nil
}

func (s *EvidenceStore) Put(contentType string, r io.Reader, size int64) (EvidenceMeta,error) {
    contentType = strings.ToLower(strings.TrimSpace(contentType))
    if size <= 0 || size > MaxEvidenceBytes { return EvidenceMeta{},ErrEvidenceInvalid }
    switch contentType {
    case "image/jpeg","image/png","image/webp","application/pdf":
    default: return EvidenceMeta{},ErrEvidenceInvalid
    }
    plain, err := io.ReadAll(io.LimitReader(r, MaxEvidenceBytes+1)); if err != nil { return EvidenceMeta{},err }
    if int64(len(plain)) != size || int64(len(plain)) > MaxEvidenceBytes { return EvidenceMeta{},ErrEvidenceInvalid }
    idBytes:=make([]byte,24); if _,err:=rand.Read(idBytes);err!=nil{return EvidenceMeta{},err}
    ref:=base64.RawURLEncoding.EncodeToString(idBytes)
    nonce:=make([]byte,s.aead.NonceSize());if _,err:=rand.Read(nonce);err!=nil{return EvidenceMeta{},err}
    header:=[]byte(contentType+"\n"+fmt.Sprint(size)+"\n")
    sealed:=s.aead.Seal(nil,nonce,plain,header)
    payload:=append(append(append([]byte{},header...),nonce...),sealed...)
    tmp:=filepath.Join(s.dir,"."+ref+".tmp")
    final:=filepath.Join(s.dir,ref+".bin")
    if err:=os.WriteFile(tmp,payload,0600);err!=nil{return EvidenceMeta{},err}
    if err:=os.Rename(tmp,final);err!=nil{_ = os.Remove(tmp);return EvidenceMeta{},err}
    return EvidenceMeta{Ref:ref,ContentType:contentType,Size:size},nil
}

func (s *EvidenceStore) Get(ref string) ([]byte,string,error) {
    if ref=="" || strings.ContainsAny(ref,"/\\.") { return nil,"",ErrEvidenceInvalid }
    payload,err:=os.ReadFile(filepath.Join(s.dir,ref+".bin"));if err!=nil{return nil,"",err}
    first:=strings.IndexByte(string(payload),'\n');if first<=0{return nil,"",ErrEvidenceInvalid}
    secondRel:=strings.IndexByte(string(payload[first+1:]),'\n');if secondRel<0{return nil,"",ErrEvidenceInvalid}
    second:=first+1+secondRel
    contentType:=string(payload[:first]); header:=payload[:second+1]
    body:=payload[second+1:]; ns:=s.aead.NonceSize();if len(body)<=ns{return nil,"",ErrEvidenceInvalid}
    plain,err:=s.aead.Open(nil,body[:ns],body[ns:],header);if err!=nil{return nil,"",err}
    return plain,contentType,nil
}

func (s *EvidenceStore) Delete(ref string) error {
    if ref=="" || strings.ContainsAny(ref,"/\\.") { return ErrEvidenceInvalid }
    err:=os.Remove(filepath.Join(s.dir,ref+".bin"));if os.IsNotExist(err){return nil};return err
}
