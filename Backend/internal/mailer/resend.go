package mailer

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

type Mailer interface {
	SendEmail(ctx context.Context, to []string, subject, htmlBody, textBody string) (string, error)
	SendPartnerInvite(ctx context.Context, partnerEmail, inviterEmail, inviteCode string) error
	SendUnlockRequest(ctx context.Context, partnerEmail, userEmail, featureName, approvalCode string) error
	SendSecurityAlert(ctx context.Context, partnerEmail, userEmail, eventType, details string) error
}

type ResendMailer struct {
	apiKey        string
	fromEmail     string
	serverBaseUrl string
	httpClient    *http.Client
	logger        *log.Logger
}

func NewResendMailer(logger *log.Logger) *ResendMailer {
	apiKey := strings.TrimSpace(os.Getenv("RESEND_API_KEY"))
	fromEmail := strings.TrimSpace(os.Getenv("RESEND_FROM_EMAIL"))
	if fromEmail == "" {
		fromEmail = "PGuard <onboarding@resend.dev>"
	}

	serverBaseUrl := strings.TrimRight(strings.TrimSpace(os.Getenv("SERVER_BASE_URL")), "/")
	if serverBaseUrl == "" {
		serverBaseUrl = "http://192.168.100.80:8080"
	}

	return &ResendMailer{
		apiKey:        apiKey,
		fromEmail:     fromEmail,
		serverBaseUrl: serverBaseUrl,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
		logger: logger,
	}
}

type resendEmailRequest struct {
	From    string   `json:"from"`
	To      []string `json:"to"`
	Subject string   `json:"subject"`
	HTML    string   `json:"html"`
	Text    string   `json:"text,omitempty"`
}

type resendEmailResponse struct {
	ID      string `json:"id,omitempty"`
	Message string `json:"message,omitempty"`
	Name    string `json:"name,omitempty"`
}

func (m *ResendMailer) SendEmail(ctx context.Context, to []string, subject, htmlBody, textBody string) (string, error) {
	if m.apiKey == "" {
		m.logger.Printf("[MAILER MOCK] RESEND_API_KEY not configured. Mocking email send to %v with subject: %s", to, subject)
		return "mock_email_id_no_api_key", nil
	}

	reqPayload := resendEmailRequest{
		From:    m.fromEmail,
		To:      to,
		Subject: subject,
		HTML:    htmlBody,
		Text:    textBody,
	}

	reqBody, err := json.Marshal(reqPayload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal email payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://api.resend.com/emails", bytes.NewReader(reqBody))
	if err != nil {
		return "", fmt.Errorf("failed to create http request: %w", err)
	}

	req.Header.Set("Authorization", "Bearer "+m.apiKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := m.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("resend request failed: %w", err)
	}
	defer resp.Body.Close()

	respBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read resend response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		m.logger.Printf("ERROR: Resend API responded with status %d: %s", resp.StatusCode, string(respBytes))

		// If in Resend Sandbox and sending to an unverified email, forward to registered account email
		if resp.StatusCode == 403 && strings.Contains(string(respBytes), "testing emails to your own email address") && len(to) > 0 && to[0] != "clintonomondiokumu@gmail.com" {
			m.logger.Printf("⚠️ Resend sandbox fallback: redirecting email intended for %v to registered account (clintonomondiokumu@gmail.com)", to)
			fallbackSubject := fmt.Sprintf("[TESTING for %s] %s", strings.Join(to, ", "), subject)
			return m.SendEmail(ctx, []string{"clintonomondiokumu@gmail.com"}, fallbackSubject, htmlBody, textBody)
		}

		return "", fmt.Errorf("resend API error (%d): %s", resp.StatusCode, string(respBytes))
	}

	var resendResp resendEmailResponse
	if err := json.Unmarshal(respBytes, &resendResp); err == nil && resendResp.ID != "" {
		m.logger.Printf("INFO: Resend email dispatched successfully. ID: %s to %v", resendResp.ID, to)
		return resendResp.ID, nil
	}

	return "", nil
}

func (m *ResendMailer) SendPartnerInvite(ctx context.Context, partnerEmail, inviterEmail, inviteCode string) error {
	subject := fmt.Sprintf("🛡️ PGuard: %s invited you as their Accountability Partner", inviterEmail)
	acceptUrl := fmt.Sprintf("%s/api/v1/accountability/accept-web?code=%s", m.serverBaseUrl, inviteCode)

	m.logger.Printf(`
======================================================================
🔗 ACCOUNTABILITY PARTNER INVITE CREATED
👉 To:          %s
👉 Invite Code: %s
👉 One-Click Web Acceptance URL:
   %s
======================================================================`, partnerEmail, inviteCode, acceptUrl)

	htmlBody := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #121212; color: #FFFFFF; padding: 24px; margin: 0; }
    .card { background-color: #1E1E22; border-radius: 16px; padding: 28px; max-width: 520px; margin: 0 auto; border: 1px solid #333333; }
    .header { color: #FF5252; font-size: 20px; font-weight: bold; margin-bottom: 12px; }
    .btn { display: inline-block; background-color: #FF5252; color: #FFFFFF !important; font-weight: bold; text-decoration: none; padding: 14px 28px; border-radius: 12px; font-size: 15px; margin: 20px 0; }
    .code-box { background-color: #28282D; border: 1px dashed #FF5252; border-radius: 10px; padding: 12px; text-align: center; font-size: 18px; font-weight: bold; letter-spacing: 3px; color: #FF5252; margin: 16px 0; }
    .footer { font-size: 12px; color: #888888; margin-top: 24px; text-align: center; }
  </style>
</head>
<body>
  <div class="card">
    <div class="header">🛡️ PGuard Accountability Request</div>
    <p>Hello,</p>
    <p><strong>%s</strong> is using <strong>PGuard</strong> to build digital self-control and protect their attention from distractions and adult content.</p>
    <p>They have chosen you as their trusted <strong>Accountability Partner</strong> to support their journey.</p>
    
    <div style="text-align: center;">
      <a href="%s" class="btn">Accept Partner Request</a>
    </div>

    <p style="font-size: 13px; color: #AAAAAA; line-height: 1.5; text-align: center;">
      💡 <em>You don't need to install any app. Tap the button above to confirm. Once accepted, whenever %s requests to disable a protection shield, you will receive an approval notification.</em>
    </p>

    <div class="footer">Sent securely by PGuard Digital Wellbeing.</div>
  </div>
</body>
</html>
`, inviterEmail, acceptUrl, inviterEmail)

	textBody := fmt.Sprintf("PGuard Accountability Request\n\n%s invited you as their Accountability Partner on PGuard.\n\nTo accept, click the link below:\n%s\n\nSent securely by PGuard.", inviterEmail, acceptUrl)

	_, err := m.SendEmail(ctx, []string{partnerEmail}, subject, htmlBody, textBody)
	return err
}

func (m *ResendMailer) SendUnlockRequest(ctx context.Context, partnerEmail, userEmail, featureName, approvalCode string) error {
	subject := fmt.Sprintf("⚠️ PGuard Alert: %s requested to unlock %s", userEmail, featureName)

	m.logger.Printf(`
======================================================================
🔑 UNLOCK APPROVAL CODE REQUESTED
👉 Partner Email: %s
👉 Feature:       %s
👉 6-Digit Code:  %s
======================================================================`, partnerEmail, featureName, approvalCode)

	htmlBody := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #121212; color: #FFFFFF; padding: 24px; }
    .card { background-color: #1E1E22; border-radius: 16px; padding: 28px; max-width: 520px; margin: 0 auto; border: 1px solid #FF5252; }
    .header { color: #FF5252; font-size: 20px; font-weight: bold; margin-bottom: 12px; }
    .code-box { background-color: #28282D; border-radius: 10px; padding: 16px; text-align: center; font-size: 28px; font-weight: bold; letter-spacing: 6px; color: #FF5252; margin: 20px 0; }
    .footer { font-size: 12px; color: #888888; margin-top: 24px; text-align: center; }
  </style>
</head>
<body>
  <div class="card">
    <div class="header">⚠️ Protection Unlock Request</div>
    <p>Hello,</p>
    <p><strong>%s</strong> is attempting to turn off protection for: <strong>%s</strong>.</p>
    <p>As their Accountability Partner, they need this 6-digit approval code to disable the block:</p>
    <div class="code-box">%s</div>
    <p style="font-size: 13px; color: #CCCCCC;">Only provide this code if you have spoken with them and both agree that turning off this shield is appropriate.</p>
    <div class="footer">PGuard Instant Security Alert</div>
  </div>
</body>
</html>
`, userEmail, featureName, approvalCode)

	textBody := fmt.Sprintf("PGuard Protection Unlock Request\n\n%s requested to disable %s.\n\nApproval Code: %s\n\nOnly share this code if you agree.", userEmail, featureName, approvalCode)

	_, err := m.SendEmail(ctx, []string{partnerEmail}, subject, htmlBody, textBody)
	return err
}

func (m *ResendMailer) SendSecurityAlert(ctx context.Context, partnerEmail, userEmail, eventType, details string) error {
	subject := fmt.Sprintf("🚨 PGuard Security Alert: %s triggered on %s's device", eventType, userEmail)

	htmlBody := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #121212; color: #FFFFFF; padding: 24px; }
    .card { background-color: #1E1E22; border-radius: 16px; padding: 28px; max-width: 520px; margin: 0 auto; border: 1px solid #FF5252; }
    .header { color: #FF5252; font-size: 20px; font-weight: bold; margin-bottom: 12px; }
    .details { background-color: #28282D; border-radius: 8px; padding: 12px; margin: 16px 0; font-size: 14px; }
    .footer { font-size: 12px; color: #888888; margin-top: 24px; text-align: center; }
  </style>
</head>
<body>
  <div class="card">
    <div class="header">🚨 Security Notification</div>
    <p><strong>%s</strong> had a security or tamper event triggered:</p>
    <div class="details">
      <strong>Event:</strong> %s<br>
      <strong>Details:</strong> %s
    </div>
    <div class="footer">PGuard Security Monitoring</div>
  </div>
</body>
</html>
`, userEmail, eventType, details)

	textBody := fmt.Sprintf("PGuard Security Alert\n\nUser: %s\nEvent: %s\nDetails: %s", userEmail, eventType, details)

	_, err := m.SendEmail(ctx, []string{partnerEmail}, subject, htmlBody, textBody)
	return err
}
