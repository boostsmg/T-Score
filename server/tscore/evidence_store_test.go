package tscore

import (
    "bytes"
    "encoding/base64"
    "os"
    "path/filepath"
    "testing"
)

func TestEvidenceStoreEncryptsAndDecrypts(t *testing.T){
    dir:=t.TempDir();key:=make([]byte,32);for i:=range key{key[i]=byte(i+1)}
    s,err:=NewEvidenceStore(dir,base64.StdEncoding.EncodeToString(key));if err!=nil{t.Fatal(err)}
    plain:=[]byte("private verification sample")
    meta,err:=s.Put("image/jpeg",bytes.NewReader(plain),int64(len(plain)));if err!=nil{t.Fatal(err)}
    raw,err:=os.ReadFile(filepath.Join(dir,meta.Ref+".bin"));if err!=nil{t.Fatal(err)}
    if bytes.Contains(raw,plain){t.Fatal("evidence was stored in plaintext")}
    got,mime,err:=s.Get(meta.Ref);if err!=nil{t.Fatal(err)}
    if mime!="image/jpeg"||!bytes.Equal(got,plain){t.Fatalf("round trip mismatch mime=%s",mime)}
}

func TestEvidenceStoreRejectsUnsupportedType(t *testing.T){
    key:=make([]byte,32);s,err:=NewEvidenceStore(t.TempDir(),base64.StdEncoding.EncodeToString(key));if err!=nil{t.Fatal(err)}
    if _,err:=s.Put("text/plain",bytes.NewReader([]byte("x")),1);err==nil{t.Fatal("unsupported type accepted")}
}
