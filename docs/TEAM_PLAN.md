# Lunch² – Ekip Planı (3 geliştirici)

> Durum: `origin/main` @ `1865ef8` (PR #19) taranarak hazırlandı.
> Amaç: sistemin bugün **ne yaptığını** açıklamak ve **kalan işleri** Dev 1 / Dev 2 / Dev 3 arasında, dosya çakışması olmadan paylaştırmak.
> Kurallar değişmedi: `AGENTS.md` (sahiplik, branch adı `d<n>/<ID>-kisa-ad`, PR ile merge, `main` her zaman build alır).

---

## 1. Kısa durum

| Alan | Durum | Kanıt |
|---|---|---|
| Backend (Quarkus) | 11 modül tamam: kayıt, giriş tokenı, davet/arkadaş, eşleştirme, grup, kartlar (Gemini + yedek), mutual connect, admin (seed/match/reset/stats) | 164 test (Türkçe Mac'te 30'u hata veriyor → bkz. **N-102**) |
| Frontend (React) | Landing, 9 adımlı kayıt (Mensa seçimli), My lunch, grup ekranı, sohbet kartları, Connect, Privacy, Admin | `lint` ✅ · 56/56 test ✅ · `build` ✅ |
| Bring a Friend (buddy) | Backend ✅ — frontend **main'de yok** (Join sayfası "later update" diyor, kayıt `inviteCode: null` gönderiyor) | Hazır dal: `d3/HACK-304-bring-a-friend` (`a541f97`) → **N-301** |
| AWS (CDK) | Kod + script + 20 test ✅ — **hiç deploy edilmedi**; main'de AI anahtarı hâlâ Anthropic | **N-201**, **N-202** |
| Dokümanlar | SPEC / ARCHITECTURE / API_CONTRACT / TASKS / DEMO var; **PRIVACY.md yok** | **N-103** |

---

## 2. Sistem nasıl çalışıyor (feature açıklamaları)

### 2.1 Kayıt (QR → ~60 sn) — *Dev 1 backend, Dev 3 frontend*
- QR kod `http://<adres>/` açar → **Find my lunch group** → 9 adım: kampüs → kategori ("Almanya'da bir süredir yaşıyorum" / "Almanya'ya yeniyim", milliyet değil) → diller (1–4) → ilgi alanları (1–5) → bölüm (opsiyonel) → **Mensa** → gün + saat (1–15 slot) → takma ad → özet + onay kutusu.
- `POST /api/participants` → backend doğrular, kişiyi `WAITING` olarak kaydeder, **tek seferlik erişim tokenı** döner (sunucuda sadece SHA-256 hash'i tutulur). Frontend tokenı `localStorage`'a yazar.
- **Geri dönüş linki** (`#/back?t=…`): başka cihazda açınca aynı kayda girer. Şifre/hesap yok.
- Mensa kataloğu: `backend/src/main/resources/catalog/mensas.json` (Würzburg'da 4, Schweinfurt'ta 1 Mensa; açılış saatlerine göre slotlar).

### 2.2 Bring a Friend (buddy) — *Dev 1 backend, Dev 3 frontend*
- My lunch → **Create my invite link** → `POST /api/me/invite` → kod + link (`#/join/<KOD>`), QR olarak da gösterilir. Bir davet = bir arkadaş; kullanılınca biter.
- Arkadaş linki açar → `GET /api/invites/<KOD>` → "Lena invited you to lunch" → kayıt `?invite=<KOD>` ile yapılır → backend **FriendPair** oluşturur.
- Uyumluluk anında kontrol edilir: farklı kampüs / farklı Mensa / ortak slot yok / ortak dil yok / davet eden zaten eşleşmiş → çift `INCOMPATIBLE` olur, sebebi My lunch'ta görünür, **Match us separately** (`POST /api/me/friend/unlink`) ile ayrılır.
- **Çift asla bölünmez**: ya ikisi aynı masaya girer ya ikisi bekler.

### 2.3 2+2 eşleştirme — *Dev 2*
- **Otomatik değil.** Admin `#/admin` → anahtar → **Run matching** → `POST /api/admin/match` (`X-Admin-Key`).
- `MatchService`: sadece `WAITING` kişileri alır → çift kontrolü (farklı Mensa → bekler) → **her Mensa için ayrı** `MatchingEngine` çalışır → her slot için tüm "2 yerel × 2 uluslararası" kombinasyonları denenir.
- **Zorunlu şartlar:** tam 4 kişi · 2+2 · aynı kampüs ve Mensa · ortak bir slot · **dördünün ortak dili** · arkadaşlar birlikte · kimse iki masada değil.
- **Puan:** arkadaş çifti +10, ortak ilgi alanları, bölüm çeşitliliği, en/de ortak dil +1, daha önce aynı masada oturanlar −8. Eşitlikte ID sırası → sonuç her zaman aynı (deterministik).
- Her grup kaydedilmeden önce `GroupValidator` ile tekrar kontrol edilir; geçersiz grup **asla** kaydedilmez.
- Eşleşmeyenler `WAITING` kalır + sebep kodu (`NOT_ENOUGH_DOMESTIC`, `NOT_ENOUGH_INTERNATIONAL`, `NO_COMMON_LANGUAGE`, `FRIEND_*`). Öğrenciler sonucu My lunch → **Refresh status** ile görür.
- 2–3 kişiyle masa **oluşmaz**. Demo tarifi: **Reset → Seed → 2 yerel kişi** (Würzburg, Mensa Röntgenring, Sal 3 Kas 12:15, English) **→ Run matching** → Aiko + Omar + o iki kişi aynı masa.

### 2.4 Grup ekranı (reveal) — *Dev 2 backend, Dev 3 frontend*
- `GET /api/groups/<id>` sadece grup üyelerine açık (diğerleri 403).
- Gösterilen: 4 takma ad, ilgi alanları, "Your friend" rozeti, tarih/saat, Mensa adı + adres, ortak ilgi alanları, "Why this group?" maddeleri ("Lena & Sam signed up together").
- Gösterilmeyen: kategori, kişi bazında diller, bölüm, iletişim bilgisi, token.

### 2.5 Sohbet kartları — *Dev 2*
- `GET /api/groups/<id>/cards`, ilk açılışta üretilir ve **gruba kaydedilir** (sonraki açılışlar API çağırmaz).
- `GEMINI_API_KEY` varsa Gemini (`gemini-3.8-flash`, 8 sn timeout); Gemini'ye **sadece ilgi alanları + dil kodu** gider (isim/ID/kampüs yok).
- Anahtar yoksa veya hata/zaman aşımı olursa **yedek kartlar** (`FallbackCards`) — kullanıcı her durumda 5 kart görür, rozet "AI" veya "Classic".

### 2.6 Mutual Connect — *Dev 1*
- Grup ekranı → **Stay in touch** → her üye için "Keep in touch" seçimi.
- İki taraf da YES derse `MUTUAL`; aksi halde "Waiting for them" — **"Hayır" asla gösterilmez**, tek taraflı istekler karşı tarafa görünmez.
- MUTUAL olunca kullanıcı **kendi yazdığı** iletişim bilgisini paylaşır; geri çekebilir. Paylaşılmadan karşı taraf hiçbir şey görmez.

### 2.7 Admin paneli — *Dev 2*
- `#/admin`, `X-Admin-Key` ile korunur (lokal: `dev-admin-key`, AWS: SSM'de rastgele).
- **Seed** (kurgusal 18 kişi + giriş linkleri) · **Run matching** · **Reset** (onaylı, her şeyi siler) · **Load metrics** (kayıt dengesi, eşleşme oranı, çiftler, mutual bağlantılar).

### 2.8 Veri ve gizlilik — *Dev 1*
- Veriler tek bir JSON dosyasında (`lunch2.data-file`); veritabanı yok. AWS'de her deploy'da sunucu yenilenir → veri silinir → **Seed tekrar**.
- **Delete my data** (`DELETE /api/me`): kişi, davetleri, çifti, bağlantı seçimleri ve paylaşılan iletişim bilgisi silinir; gruplarda "Former participant" kalır; token anında geçersiz.
- Toplanmayan: e-posta, telefon, milliyet, yaş, cinsiyet, fotoğraf.

### 2.9 Deploy (AWS CDK) — *Dev 2*
- `infra/`: tek `t3.micro` EC2 + sabit Elastic IP, sadece port 80, NAT/load balancer/veritabanı yok. Quarkus hem API'yi hem frontend'i sunar.
- `./scripts/deploy.sh`: derler → gizli bilgileri SSM'e yazar → `cdk bootstrap` + `cdk deploy` → uygulama açılana kadar bekler → `http://<IP>` yazdırır. `./scripts/destroy.sh` siler.
- Sadece HTTP (IP'ye TLS sertifikası alınamaz) → telefonda kopyala/paylaş çalışmayabilir; linkler ekranda görünür kalmalı.

---

## 3. Kalan işler — kişi bazında

Etiketler: **P0** = demo için şart · **P1** = planda var, demoyu bloklamıyor · **P2** = zaman kalırsa.
Süreler tahmindir.

### Dev 1 — Platform, kimlik, davet, connect, dokümanlar (+ entegrasyon lideri)

| ID | İş | Öncelik | Süre | Bağımlılık |
|---|---|---|---|---|
| N-101 | Buddy PR'ını gözden geçir ve merge et (entegrasyon lideri) | P0 | 15 dk | N-301 |
| N-102 | Test locale düzeltmesini onayla (buddy PR'ında `backend/pom.xml`) | P0 | 5 dk | N-301 |
| N-103 | `docs/PRIVACY.md` yaz (HACK-110) | P1 | 15 dk | – |
| N-104 | Davet önizlemesine `mensaId` ekle | P1 | 20 dk | N-101 |
| N-105 | Connect'i yemekten sonra açan ayar (opsiyonel) | P2 | 20 dk | – |

- **N-101 – Buddy PR merge.** Dev 3 rebase'i bitirince PR'ı aç/incele: üç işi birlikte getiriyor (buddy arayüzü, test locale, Gemini deploy). *Kabul:* `./mvnw -q verify` (164/164), `npm run lint && npm test && npm run build`, `cd infra && npm test` (20/20) yeşil; Join sayfasında "later update" yazısı yok.
- **N-102 – Test locale.** Sebep: macOS dili Türkçe (`tr_DE`) olunca RestAssured her hata cevabında (400/401/403/404/409/500) istisna atıyor → 30 test hata. Düzeltme: surefire `argLine`'a `-Duser.language=en -Duser.country=US`. Üretim etkilenmiyor (sunucu zaten İngilizce locale). *Kabul:* Türkçe Mac'te `./mvnw -q verify` 164/164.
- **N-103 – PRIVACY.md.** Dosya: `docs/PRIVACY.md`. İçerik: tutulan veriler (2.8), amaç, hukuki dayanak varsayımı (açık rıza), saklama (prod önerisi: son yemekten 30 gün sonra sil), silme (`DELETE /api/me`), servisler (AWS eu-central-1, Gemini — sadece ilgi alanı + dil), production öncesi eksikler (`docs/SPEC.md` "Privacy: production gaps"). *Kabul:* README'den link verilmiş, tek sayfa.
- **N-104 – Önizlemede Mensa.** Sorun: arkadaş farklı Mensa seçerse çift `FRIEND_DIFFERENT_MENSA` ile bekliyor; "arkadaşınla otur" sözü kolay bozuluyor. Dosyalar: `invite/InvitePreview.java`, `invite/InviteService.java`, `docs/API_CONTRACT.md` (E6'ya **opsiyonel** `mensaId` — sadece ekleme), test `InviteResourceTest`. *Kabul:* `GET /api/invites/<KOD>` → `mensaId` dolu; eski alanlar aynı. Dev 3'e haber ver (N-302).
- **N-105 – Connect zamanlaması (P2).** `lunch2.connect.require-after-start` (varsayılan `false`, demo bozulmasın); `true` iken `startsAt` öncesi 409. Sadece vakit kalırsa.

### Dev 2 — Eşleştirme, kartlar, admin, AWS deploy, demo

| ID | İş | Öncelik | Süre | Bağımlılık |
|---|---|---|---|---|
| N-201 | AWS'ye ilk gerçek deploy + telefon testi | P0 | 30 dk | N-101 |
| N-202 | Gemini anahtarının AWS'de çalıştığını doğrula | P0 | 10 dk | N-201 |
| N-203 | `docs/DEMO.md`'yi AWS ve demo tarifine göre güncelle | P1 | 15 dk | N-201 |
| N-204 | "We met" check-in (backend + istatistik) | P2 | 30 dk | – |
| N-205 | Seed'e canlı demo için ikinci "2 kişi bekliyor" masası (opsiyonel) | P2 | 15 dk | – |

- **N-201 – İlk deploy.** Hazırlık: `brew install awscli && brew install --cask session-manager-plugin`, `aws configure`. Temiz + güncel `main`'den: `GEMINI_API_KEY=... ./scripts/deploy.sh`. Script çıktısındaki `http://<IP>`'yi **mobil veriyle** telefondan aç. *Kabul:* telefonda kayıt → My lunch; `curl http://<IP>/api/ping` → `{"ok":true}`; admin anahtarı `aws ssm get-parameter --name /lunch2/admin-key --with-decryption --query Parameter.Value --output text` ile okunuyor.
- **N-202 – Gemini.** Buddy PR'ı ile deploy `/lunch2/gemini-api-key` → `GEMINI_API_KEY` kullanıyor (main'de hâlâ Anthropic — PR merge olmadan deploy etme). *Kabul:* AWS'de bir grubun kart rozeti "AI"; anahtar yokken "Classic".
- **N-203 – DEMO.md.** Eklenecekler: canlı adres, admin anahtarını alma komutu, demo sırası (Reset → Seed → 2 canlı kayıt davetle → Run matching → reveal → kartlar → connect), 2.3'teki "hangi slot seçilecek" tarifi, her deploy sonrası Seed hatırlatması.
- **N-204 – Katılım (P2).** `POST /api/groups/<id>/attendance` (üye, idempotent) + `StatsResponse`'a `attendanceRate` (opsiyonel alan). Dev 3 N-305 ile butonu ekler. Sözleşmeye ekleme Dev 1 üzerinden.
- **N-205 – Seed (P2).** İkinci bir "2 uluslararası bekliyor" slotu eklenirse iki ekip aynı anda canlı demo yapabilir. Mevcut seed testlerini güncelle.

### Dev 3 — Frontend deneyimi

| ID | İş | Öncelik | Süre | Bağımlılık |
|---|---|---|---|---|
| N-301 | Buddy dalını main'e rebase et, çakışmayı çöz, PR aç | P0 | 20 dk | – |
| N-302 | Davetle gelen arkadaşa Mensa'yı önceden seç | P1 | 20 dk | N-104 |
| N-303 | Geri dönüş linkini her zaman görünür yap (HTTP) | P1 | 10 dk | – |
| N-304 | Mock modunda buddy akışını kontrol et | P1 | 10 dk | N-301 |
| N-305 | Grup ekranına "We met ✓" butonu | P2 | 20 dk | N-204 |

- **N-301 – Buddy rebase.** Dal: `d3/HACK-304-bring-a-friend` (worktree: `~/Desktop/hackathon-buddy`). `git rebase origin/main` → çakışma sadece `frontend/src/pages/RegisterPage.tsx` (main'deki HACK-309 değişiklikleriyle). Korunacaklar: `useSearchParams` ile `?invite=`, davetten kampüs ön seçimi, `inviteCode: invite.data?.valid ? inviteCode : null`, davet bandı + "Continue without the invite". *Kabul:* tüm testler yeşil (N-101'deki liste), tarayıcıda: davet oluştur → link → Join → kayıt → ikisi de "Your linked friend" görüyor → eşleşince "Your friend" rozeti.
- **N-302 – Mensa ön seçimi.** N-104'ten sonra: `RegisterPage` davet önizlemesindeki `mensaId`'yi ön seçer ve "Lena ile aynı Mensa" notu gösterir; kullanıcı değiştirirse uyarı. `types.ts` + `mocks.ts`'e opsiyonel `mensaId`. *Kabul:* davetle gelen kişi Mensa adımına geldiğinde arkadaşının Mensa'sı seçili.
- **N-303 – Görünür linkler.** `components/ReturnLink.tsx`: link input'u her zaman göster (şu an sadece kopyalama başarısız olunca çıkıyor). HTTP IP'de `navigator.clipboard` engelli. *Kabul:* telefonda (HTTP) link okunabilir ve seçilebilir.
- **N-304 – Mock kontrol.** `VITE_USE_MOCKS=true` ile davet → Join → kayıt → Admin'de seed/match → grup ekranı. Hata varsa `api/mocks.ts`'i düzelt.
- **N-305 – We met (P2).** N-204'ten sonra grup ekranına buton; tıklayınca "Thanks!" ve tekrar basılamaz.

### Herkes — Demo hazırlığı (feature freeze'den sonra)

| ID | İş | Kim | Süre |
|---|---|---|---|
| N-401 | Canlı adreste uçtan uca prova (2.3'teki tarif) | Hepsi | 20 dk |
| N-402 | Yedek ekran kaydı (prova sırasında) | Dev 3 | 10 dk |
| N-403 | Sunum: problem → neden Almanlar gelir ("Bring a friend") → demo → metrikler → GDPR → maliyet | Dev 1 | 30 dk |

---

## 4. Sıra ve bağımlılıklar

```
Dev 3: N-301 ──▶ Dev 1: N-101/N-102 (merge) ──▶ Dev 2: N-201 ──▶ N-202 ──▶ N-203
                                   │
                                   └──▶ Dev 1: N-104 ──▶ Dev 3: N-302
Paralel, bağımsız: Dev 1 N-103 · Dev 3 N-303, N-304 · (P2) Dev 2 N-204 ──▶ Dev 3 N-305
Sonda: N-401 → N-402 → N-403
```

- **Kritik yol:** N-301 → N-101 → N-201. Buddy PR'ı girmeden AWS'ye deploy etme (Gemini düzeltmesi o PR'da).
- **Dosya çakışması yok:** Dev 1 `invite/`, `docs/PRIVACY.md`, `API_CONTRACT.md`; Dev 2 `infra/`, `scripts/`, `admin/`, `docs/DEMO.md`; Dev 3 `frontend/`.
- **Sözleşme değişikliği** (N-104, N-204): sadece opsiyonel alan ekleme; Dev 1 `API_CONTRACT.md`'yi günceller, Dev 3 `types.ts`/`mocks.ts`'i.

---

## 5. Herkes için hızlı kontrol listesi

```bash
cd backend && ./mvnw -q verify                                   # 164 test
cd frontend && npm run lint && npm test && npm run build          # 56 test (buddy ile artar)
cd infra && npm test                                              # 20 test (CDK + script)
```

Lokal çalıştırma: `cd backend && ./mvnw quarkus:dev` + `cd frontend && npm run dev` → http://localhost:5173 (admin: `#/admin`, anahtar `dev-admin-key`).
