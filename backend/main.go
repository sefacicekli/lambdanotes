package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
)

const (
	notesDir   = "./notes"
	configFile = "./config.json"
	port       = ":8080"

	githubClientID     = "Ov23liq3HVgaBCjZ6Pda"
	githubClientSecret = "32560715b1ffd6ce2eb0474d8f23ac7948d24efd"
)

type Note struct {
	Filename string `json:"filename"`
	Content  string `json:"content"`
}

type AppConfig struct {
	RepoURL  string `json:"repoUrl"`
	Token    string `json:"token"`
	Username string `json:"username"`
	Email    string `json:"email"`
}

type SyncRequest struct {
	Message string `json:"message"`
}

var (
	mu     sync.Mutex
	config AppConfig
)

type MoveRequest struct {
	OldPath string `json:"oldPath"`
	NewPath string `json:"newPath"`
}

type GithubDeviceCodeResponse struct {
	DeviceCode      string `json:"device_code"`
	UserCode        string `json:"user_code"`
	VerificationURI string `json:"verification_uri"`
	ExpiresIn       int    `json:"expires_in"`
	Interval        int    `json:"interval"`
}

type GithubTokenResponse struct {
	AccessToken string `json:"access_token"`
	TokenType   string `json:"token_type"`
	Scope       string `json:"scope"`
	Error       string `json:"error"`
	ErrorDesc   string `json:"error_description"`
}

func main() {
	fmt.Println("Starting backend...")
	loadConfig()

	// Not klasörünü oluştur
	if _, err := os.Stat(notesDir); os.IsNotExist(err) {
		os.Mkdir(notesDir, 0755)
	}

	// .gitignore oluştur (Sadece markdown dosyalarını takip et)
	gitignorePath := filepath.Join(notesDir, ".gitignore")
	if _, err := os.Stat(gitignorePath); os.IsNotExist(err) {
		ioutil.WriteFile(gitignorePath, []byte("*\n!*.md\n!*/\n"), 0644)
	}

	http.HandleFunc("/api/notes", handleNotes)       // GET (list), POST (save)
	http.HandleFunc("/api/notes/", handleNoteDetail) // GET (read), DELETE (delete)
	http.HandleFunc("/api/move", handleMove)         // POST (move/rename)
	http.HandleFunc("/api/sync", handleSync)         // POST (git sync)
	http.HandleFunc("/api/config", handleConfig)     // GET, POST (setup)
	http.HandleFunc("/api/auth/github/start", handleGithubAuthStart)
	http.HandleFunc("/api/auth/github/poll", handleGithubAuthPoll)

	log.Printf("Backend servisi %s portunda çalışıyor...", port)
	log.Fatal(http.ListenAndServe(port, nil))
}

func handleMove(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if r.Method != "POST" {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req MoveRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	mu.Lock()
	defer mu.Unlock()

	oldPath := filepath.Join(notesDir, req.OldPath)
	newPath := filepath.Join(notesDir, req.NewPath)

	// Hedef klasör yoksa oluştur
	if err := os.MkdirAll(filepath.Dir(newPath), 0755); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if err := os.Rename(oldPath, newPath); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}

func handleNotes(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if r.Method == "GET" {
		var notes []string
		err := filepath.Walk(notesDir, func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			// .git klasörünü yoksay
			if strings.Contains(path, ".git") {
				return nil
			}

			if !info.IsDir() && strings.HasSuffix(info.Name(), ".md") {
				rel, err := filepath.Rel(notesDir, path)
				if err == nil {
					notes = append(notes, filepath.ToSlash(rel))
				}
			}
			return nil
		})

		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		json.NewEncoder(w).Encode(notes)
	} else if r.Method == "POST" {
		var note Note
		if err := json.NewDecoder(r.Body).Decode(&note); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		mu.Lock()
		defer mu.Unlock()

		// Güvenlik kontrolü: .. ile üst dizine çıkmayı engelle
		if strings.Contains(note.Filename, "..") {
			http.Error(w, "Invalid filename", http.StatusBadRequest)
			return
		}

		path := filepath.Join(notesDir, note.Filename)
		if !strings.HasSuffix(path, ".md") {
			path += ".md"
		}

		// Alt klasör varsa oluştur
		if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		err := ioutil.WriteFile(path, []byte(note.Content), 0644)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
	}
}

func handleNoteDetail(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	filename := strings.TrimPrefix(r.URL.Path, "/api/notes/")
	path := filepath.Join(notesDir, filename)

	if r.Method == "GET" {
		content, err := ioutil.ReadFile(path)
		if err != nil {
			http.Error(w, "Not found", http.StatusNotFound)
			return
		}
		json.NewEncoder(w).Encode(Note{Filename: filename, Content: string(content)})
	} else if r.Method == "DELETE" {
		mu.Lock()
		defer mu.Unlock()
		os.Remove(path)
		w.WriteHeader(http.StatusOK)
	}
}

func handleSync(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if r.Method != "POST" {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	if config.RepoURL == "" || config.Token == "" {
		http.Error(w, "Git yapılandırması eksik. Lütfen ayarlardan GitHub bağlantısını yapın.", http.StatusBadRequest)
		return
	}

	var req SyncRequest
	json.NewDecoder(r.Body).Decode(&req)

	mu.Lock()
	defer mu.Unlock()

	// 1. Değişiklikleri analiz et (Git status)
	statusCmd := exec.Command("git", "status", "--porcelain")
	statusCmd.Dir = notesDir
	statusOutput, _ := statusCmd.Output()

	changes := string(statusOutput)
	var added, modified, deleted []string

	lines := strings.Split(changes, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if len(line) < 3 {
			continue
		}

		code := line[:2]
		file := strings.TrimSpace(line[2:])

		if strings.Contains(code, "??") || strings.Contains(code, "A") {
			added = append(added, file)
		} else if strings.Contains(code, "M") {
			modified = append(modified, file)
		} else if strings.Contains(code, "D") {
			deleted = append(deleted, file)
		}
	}

	// 2. Commit mesajı oluştur
	var msgParts []string
	if len(added) > 0 {
		msgParts = append(msgParts, fmt.Sprintf("Added: %s", strings.Join(added, ", ")))
	}
	if len(modified) > 0 {
		msgParts = append(msgParts, fmt.Sprintf("Updated: %s", strings.Join(modified, ", ")))
	}
	if len(deleted) > 0 {
		msgParts = append(msgParts, fmt.Sprintf("Deleted: %s", strings.Join(deleted, ", ")))
	}

	commitMsg := req.Message
	if len(msgParts) > 0 {
		commitMsg = strings.Join(msgParts, "; ")
	} else if commitMsg == "" {
		commitMsg = "Auto sync: No changes detected"
	}

	// Git işlemleri
	if err := runGitCommand("add", "."); err != nil {
		log.Println("Git add error:", err)
	}

	if err := runGitCommand("commit", "-m", commitMsg); err != nil {
		log.Println("Git commit error (might be empty):", err)
	}

	if err := runGitCommand("pull", "--rebase", "origin", "main"); err != nil {
		log.Println("Git pull error:", err)
		// Pull hatası olsa bile push denenebilir veya kullanıcıya bildirilebilir
	}

	if err := runGitCommand("push", "origin", "main"); err != nil {
		log.Println("Git push error:", err)
		http.Error(w, "Senkronizasyon hatası: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "synced"})
}

func loadConfig() {
	data, err := ioutil.ReadFile(configFile)
	if err == nil {
		json.Unmarshal(data, &config)
	}
}

func saveConfig() {
	data, _ := json.MarshalIndent(config, "", "  ")
	ioutil.WriteFile(configFile, data, 0644)
}

func getAuthRepoURL() string {
	if config.Token == "" || config.RepoURL == "" {
		return ""
	}
	// https://github.com/user/repo.git -> https://oauth2:TOKEN@github.com/user/repo.git
	parts := strings.Split(config.RepoURL, "://")
	if len(parts) != 2 {
		return config.RepoURL
	}
	return fmt.Sprintf("%s://oauth2:%s@%s", parts[0], config.Token, parts[1])
}

func runGitCommand(args ...string) error {
	cmd := exec.Command("git", args...)
	cmd.Dir = notesDir
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	output, err := cmd.CombinedOutput()
	if err != nil {
		log.Printf("Git Error: %s\nOutput: %s", err, string(output))
		return fmt.Errorf("%s: %s", err, string(output))
	}
	return nil
}

func handleConfig(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if r.Method == "GET" {
		json.NewEncoder(w).Encode(config)
	} else if r.Method == "POST" {
		var newConfig AppConfig
		if err := json.NewDecoder(r.Body).Decode(&newConfig); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		mu.Lock()
		defer mu.Unlock()

		config = newConfig
		saveConfig()

		// Git yapılandırması
		// 1. Git init (eğer yoksa)
		if _, err := os.Stat(filepath.Join(notesDir, ".git")); os.IsNotExist(err) {
			runGitCommand("init")
			runGitCommand("branch", "-M", "main")
		}

		// 2. Config ayarları
		runGitCommand("config", "user.name", config.Username)
		runGitCommand("config", "user.email", config.Email)

		// 3. Remote ekle/güncelle
		runGitCommand("remote", "remove", "origin")
		authURL := getAuthRepoURL()
		if err := runGitCommand("remote", "add", "origin", authURL); err != nil {
			http.Error(w, "Remote eklenemedi: "+err.Error(), http.StatusInternalServerError)
			return
		}

		// 4. İlk pull (eğer repo boş değilse)
		if err := runGitCommand("pull", "origin", "main", "--allow-unrelated-histories"); err != nil {
			log.Println("Initial pull failed (repo might be empty or branch is not main):", err)
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "configured"})
	}
}

func handleGithubAuthStart(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if r.Method != "POST" {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// GitHub Device Flow başlat
	data := url.Values{}
	data.Set("client_id", githubClientID)
	data.Set("scope", "repo") // Repo erişimi için scope

	req, err := http.NewRequest("POST", "https://github.com/login/device/code", strings.NewReader(data.Encode()))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	body, _ := ioutil.ReadAll(resp.Body)
	w.Write(body)
}

func handleGithubAuthPoll(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if r.Method != "POST" {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		DeviceCode string `json:"device_code"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	data := url.Values{}
	data.Set("client_id", githubClientID)
	data.Set("device_code", req.DeviceCode)
	data.Set("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
	// Client Secret is optional for public apps but recommended if available.
	// Since we have it, let's include it to be safe, although for device flow it might not be strictly enforced for all app types.
	// However, standard OAuth flow usually requires it. Let's try with it.
	data.Set("client_secret", githubClientSecret)

	postReq, err := http.NewRequest("POST", "https://github.com/login/oauth/access_token", strings.NewReader(data.Encode()))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	postReq.Header.Set("Accept", "application/json")
	postReq.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	client := &http.Client{}
	resp, err := client.Do(postReq)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	// Yanıtı oku
	body, _ := ioutil.ReadAll(resp.Body)

	// Eğer başarılı bir token geldiyse, config'e kaydet
	var tokenResp GithubTokenResponse
	if err := json.Unmarshal(body, &tokenResp); err == nil && tokenResp.AccessToken != "" {
		mu.Lock()
		config.Token = tokenResp.AccessToken
		// Kullanıcı adını henüz bilmiyoruz ama token var.
		// İstenirse token ile user info çekilebilir ama şimdilik sadece token'ı kaydedelim.
		saveConfig()
		mu.Unlock()
	}

	w.Write(body)
}
