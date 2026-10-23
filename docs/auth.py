P=0x9999
SEQ=bytes([0x11,0x22,0x33,0x33,0x22,0x11]*2+[0x11,0x22,0x33,0x33])
C=[[2,1,1,1,4,2,1,1,2,2,4,2,4,4,16,8],[2,1,1,1,4,2,1,1,1,1,2,1,2,2,8,4],[1,1,4,2,2,2,4,2,16,8,4,4,2,1,1,1],[1,1,4,2,1,1,2,1,8,4,2,2,2,1,1,1],[16,8,2,2,4,2,4,4,1,1,4,2,1,1,2,1],[8,4,1,1,2,1,2,2,1,1,4,2,1,1,2,1],[2,2,4,2,4,4,16,8,2,1,1,1,4,2,1,1],[1,1,2,1,2,2,8,4,2,1,1,1,4,2,1,1],[4,2,4,4,16,8,2,2,1,1,2,1,1,1,4,2],[2,1,2,2,8,4,1,1,1,1,2,1,1,1,4,2],[4,4,16,8,1,1,2,1,4,2,1,1,4,2,2,2],[2,2,8,4,1,1,2,1,4,2,1,1,2,1,1,1],[1,1,2,1,1,1,4,2,4,4,16,8,2,2,4,2],[1,1,2,1,1,1,4,2,2,2,8,4,1,1,2,1],[4,2,1,1,2,1,1,1,4,2,2,2,16,8,4,4],[4,2,1,1,2,1,1,1,2,1,1,1,8,4,2,2]]
exp=[0 if i==128 else pow(45,i,257) for i in range(256)]
log=[0]*256;log[0]=128
for i in range(1,256):
    m=pow(45,i,257)
    if m!=256: log[m]=i
bias=[[ (lambda v:0 if v==256 else v)(pow(45,pow(45,17*(i+2)+j+1,257),257)) for j in range(16)] for i in range(16)]
def ks(k):
    k=list(k);k[15]^=6;keys=[k[:]];r=k[:]+[0]
    x=0
    for b in k:x^=b
    r[16]=x
    for ki in range(1,17):
        r=[((b>>5)|(b<<3))&255 for b in r]
        keys.append([(r[(ki+i)%17]+bias[ki-1][i])&255 for i in range(16)])
    return keys
def bit(i):return (1<<i)&P
def enc(pt,K):
    c=list(pt)
    for rd in range(8):
        if rd==2: c=[(c[i]^pt[i]) if bit(i) else (c[i]+pt[i])&255 for i in range(16)]
        c=[(c[i]^K[2*rd][i]) if bit(i) else (c[i]+K[2*rd][i])&255 for i in range(16)]
        c=[exp[c[i]] if bit(i) else log[c[i]] for i in range(16)]
        c=[(K[2*rd+1][i]+c[i])&255 if bit(i) else K[2*rd+1][i]^c[i] for i in range(16)]
        cc=c[:];c=[sum(C[i][j]*cc[j] for j in range(16))&255 for i in range(16)]
    return bytes((K[16][i]^c[i]) if bit(i) else (K[16][i]+c[i])&255 for i in range(16))
def resp(ch):return enc(SEQ,ks(ch))
if __name__=='__main__':
    print(resp(bytes.fromhex('abb2cdc69bb454110e827441213ddc87')).hex(),'expect 25721bd38bd8685e0c96843f6ca2eb2b')
    print(resp(bytes.fromhex('1edd21d1aefcdfabc31ca55d806cc8aa')).hex(),'expect bd8dd81133b3f22080b918f8122d9e59')
