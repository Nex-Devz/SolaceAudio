#!/usr/bin/env bash
# ==============================================================================
#  CLOUDFLARE WARP SOCKS5 PROXY INSTALLER (FOR LAVALINK / YOUTUBE)
#  Author: TitanX / SolaceAudio
# ==============================================================================

set -e

# Colors & Formatting
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

clear

# UI Banner
echo -e "${CYAN}${BOLD}"
cat << "EOF"
  â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ•—     â–ˆâ–ˆâ•—   â–ˆâ–ˆâ•— â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•— â–ˆâ–ˆâ•—   â–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•— â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•— â–ˆâ–ˆâ–ˆâ•—   â–ˆâ–ˆâ•—
  â–ˆâ–ˆâ•”â•â•â•â•â•â–ˆâ–ˆâ•‘     â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•”â•â•â•â•â• â•šâ–ˆâ–ˆâ•— â–ˆâ–ˆâ•”â•â•šâ•â•â–ˆâ–ˆâ–ˆâ•”â•â–ˆâ–ˆâ•”â•â•â•â•â•â–ˆâ–ˆâ•”â•â•â•â–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ•—  â–ˆâ–ˆâ•‘
  â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ•‘     â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘  â–ˆâ–ˆâ–ˆâ•— â•šâ–ˆâ–ˆâ–ˆâ–ˆâ•”â•   â–ˆâ–ˆâ–ˆâ•”â• â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—  â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•”â–ˆâ–ˆâ•— â–ˆâ–ˆâ•‘
  â•šâ•â•â•â•â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘     â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘  â•šâ–ˆâ–ˆâ•”â•   â–ˆâ–ˆâ–ˆâ•”â•  â–ˆâ–ˆâ•”â•â•â•  â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ•‘â–ˆâ–ˆâ•‘â•šâ–ˆâ–ˆâ•—â–ˆâ–ˆâ•‘
  â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•‘â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â•šâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•”â•â•šâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•”â•   â–ˆâ–ˆâ•‘   â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•—â•šâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ–ˆâ•”â•â–ˆâ–ˆâ•‘ â•šâ–ˆâ–ˆâ–ˆâ–ˆâ•‘
  â•šâ•â•â•â•â•â•â•â•šâ•â•â•â•â•â•â• â•šâ•â•â•â•â•â•  â•šâ•â•â•â•â•â•    â•šâ•â•   â•šâ•â•â•â•â•â•â•â•šâ•â•â•â•â•â•â• â•šâ•â•â•â•â•â• â•šâ•â•  â•šâ•â•â•â•
EOF
echo -e "${PURPLE}  âš¡ Cloudflare WARP SOCKS5 Proxy Automated Setup for Lavalink âš¡${NC}"
echo -e "${BLUE}  ===============================================================${NC}\n"

# Helper Functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[âœ”]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
error() { echo -e "${RED}[âœ–]${NC} $1"; exit 1; }

# Step 1: Root Permission Check
info "Checking permissions..."
if [ "$EUID" -ne 0 ]; then
    error "Please run this installer as root (use: sudo bash install.sh)"
fi
success "Root permissions verified."

# Step 2: System Architecture Check
ARCH=$(dpkg --print-architecture 2>/dev/null || uname -m)
if [ "$ARCH" != "amd64" ] && [ "$ARCH" != "x86_64" ]; then
    error "Cloudflare WARP officially requires amd64 / x86_64 architecture. Detected: $ARCH"
fi
success "Architecture ($ARCH) compatible."

# Step 3: Install Core Dependencies
echo ""
info "Updating apt packages and installing dependencies (curl, gpg, lsb-release)..."
apt-get update -qq >/dev/null 2>&1
apt-get install -y -qq curl gpg lsb-release >/dev/null 2>&1
success "Dependencies installed."

# Step 4: Add Cloudflare GPG Key & Repository
echo ""
info "Adding Cloudflare official repository & signing keys..."
mkdir -p /usr/share/keyrings
curl -fsSL https://pkg.cloudflareclient.com/pubkey.gpg | gpg --yes --dearmor -o /usr/share/keyrings/cloudflare-warp-archive-keyring.gpg
UBUNTU_CODENAME=$(lsb_release -cs)
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/cloudflare-warp-archive-keyring.gpg] https://pkg.cloudflareclient.com/ ${UBUNTU_CODENAME} main" > /etc/apt/sources.list.d/cloudflare-client.list
success "Repository added for Ubuntu release: ${UBUNTU_CODENAME}."

# Step 5: Install cloudflare-warp
echo ""
info "Installing cloudflare-warp package..."
apt-get update -qq >/dev/null 2>&1
apt-get install -y -qq cloudflare-warp >/dev/null 2>&1
success "cloudflare-warp successfully installed."

# Step 6: Configure WARP in Proxy Mode
echo ""
info "Configuring WARP service..."
systemctl enable --now warp-svc >/dev/null 2>&1 || true
sleep 2

# Register / Reset Client
warp-cli --accept-tos registration new >/dev/null 2>&1 || warn "Registration already exists or refreshed."

# Configure Proxy mode and Port 40000
warp-cli --accept-tos mode proxy >/dev/null 2>&1
warp-cli --accept-tos proxy port 40000 >/dev/null 2>&1
warp-cli --accept-tos connect >/dev/null 2>&1

sleep 3

# Step 7: Verify Connectivity
echo ""
info "Testing proxy connection through 127.0.0.1:40000..."
TEST_IP=$(curl -s -x socks5://127.0.0.1:40000 --max-time 10 https://ipinfo.io/ip 2>/dev/null || echo "")

if [ -n "$TEST_IP" ]; then
    success "Connection test passed! Proxy IP: ${BOLD}${GREEN}${TEST_IP}${NC}"
else
    warn "Proxy test timed out on first attempt. WARP daemon might take a few seconds to warm up."
fi

# Print Success Summary Box
echo ""
echo -e "${GREEN}${BOLD}===============================================================${NC}"
echo -e "${GREEN}${BOLD}   ðŸŽ‰ CLOUDFLARE WARP PROXY SETUP COMPLETED SUCCESSFULLY!      ${NC}"
echo -e "${GREEN}${BOLD}===============================================================${NC}"
echo -e "${CYAN}Proxy Protocol : ${WHITE}${BOLD}SOCKS5${NC}"
echo -e "${CYAN}Local Host     : ${WHITE}${BOLD}127.0.0.1${NC}"
echo -e "${CYAN}Proxy Port     : ${WHITE}${BOLD}40000${NC}"
echo -e "${CYAN}Full URL       : ${GREEN}${BOLD}socks5://127.0.0.1:40000${NC}"
echo -e "${GREEN}---------------------------------------------------------------${NC}"
echo -e "${YELLOW}${BOLD}Add this to your Lavalink application.yml:${NC}"
echo -e "${WHITE}"
cat << "YAML"
plugins:
  youtube:
    enabled: true
    proxy:
      url: "socks5://127.0.0.1:40000"
YAML
echo -e "${GREEN}---------------------------------------------------------------${NC}"
echo -e "${PURPLE}Useful Commands:${NC}"
echo -e "  â€¢ Check Status : ${WHITE}warp-cli status${NC}"
echo -e "  â€¢ Test Proxy   : ${WHITE}curl -x socks5://127.0.0.1:40000 https://ipinfo.io${NC}"
echo -e "  â€¢ Reconnect    : ${WHITE}warp-cli connect${NC}"
echo -e "${GREEN}${BOLD}===============================================================${NC}\n"

