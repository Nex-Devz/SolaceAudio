package com.solaceaudio.plugin.protocol;

public class Config {

    private GaanaConfig gaana;
    private AmazonMusicConfig amazonmusic;
    private SpotifyConfig spotify;
    private PandoraConfig pandora;
    private YouTubeConfig youtube;

    public GaanaConfig getGaana() {
        return this.gaana;
    }

    public void setGaana(GaanaConfig gaana) {
        this.gaana = gaana;
    }

    public AmazonMusicConfig getAmazonmusic() {
        return this.amazonmusic;
    }

    public void setAmazonmusic(AmazonMusicConfig amazonmusic) {
        this.amazonmusic = amazonmusic;
    }

    public SpotifyConfig getSpotify() {
        return this.spotify;
    }

    public void setSpotify(SpotifyConfig spotify) {
        this.spotify = spotify;
    }

    public PandoraConfig getPandora() {
        return this.pandora;
    }

    public void setPandora(PandoraConfig pandora) {
        this.pandora = pandora;
    }

    public YouTubeConfig getYoutube() {
        return this.youtube;
    }

    public void setYoutube(YouTubeConfig youtube) {
        this.youtube = youtube;
    }

    public static class GaanaConfig {
        private int playlistLoadLimit;
        private int albumLoadLimit;
        private int artistLoadLimit;

        public int getPlaylistLoadLimit() {
            return this.playlistLoadLimit;
        }

        public void setPlaylistLoadLimit(int playlistLoadLimit) {
            this.playlistLoadLimit = playlistLoadLimit;
        }

        public int getAlbumLoadLimit() {
            return this.albumLoadLimit;
        }

        public void setAlbumLoadLimit(int albumLoadLimit) {
            this.albumLoadLimit = albumLoadLimit;
        }

        public int getArtistLoadLimit() {
            return this.artistLoadLimit;
        }

        public void setArtistLoadLimit(int artistLoadLimit) {
            this.artistLoadLimit = artistLoadLimit;
        }
    }

    public static class AmazonMusicConfig {
        private int playlistLoadLimit;
        private int albumLoadLimit;
        private int artistLoadLimit;

        public int getPlaylistLoadLimit() {
            return this.playlistLoadLimit;
        }

        public void setPlaylistLoadLimit(int playlistLoadLimit) {
            this.playlistLoadLimit = playlistLoadLimit;
        }

        public int getAlbumLoadLimit() {
            return this.albumLoadLimit;
        }

        public void setAlbumLoadLimit(int albumLoadLimit) {
            this.albumLoadLimit = albumLoadLimit;
        }

        public int getArtistLoadLimit() {
            return this.artistLoadLimit;
        }

        public void setArtistLoadLimit(int artistLoadLimit) {
            this.artistLoadLimit = artistLoadLimit;
        }
    }

    public static class SpotifyConfig {
        private String spDc;
        private int playlistLoadLimit;
        private int albumLoadLimit;
        private Boolean resolveArtistsInSearch;
        private Boolean localFiles;

        public String getSpDc() {
            return this.spDc;
        }

        public void setSpDc(String spDc) {
            this.spDc = spDc;
        }

        public int getPlaylistLoadLimit() {
            return this.playlistLoadLimit;
        }

        public void setPlaylistLoadLimit(int playlistLoadLimit) {
            this.playlistLoadLimit = playlistLoadLimit;
        }

        public int getAlbumLoadLimit() {
            return this.albumLoadLimit;
        }

        public void setAlbumLoadLimit(int albumLoadLimit) {
            this.albumLoadLimit = albumLoadLimit;
        }

        public Boolean getResolveArtistsInSearch() {
            return this.resolveArtistsInSearch;
        }

        public void setResolveArtistsInSearch(Boolean resolveArtistsInSearch) {
            this.resolveArtistsInSearch = resolveArtistsInSearch;
        }

        public Boolean getLocalFiles() {
            return this.localFiles;
        }

        public void setLocalFiles(Boolean localFiles) {
            this.localFiles = localFiles;
        }

        private String countryCode;

        public String getCountryCode() {
            return this.countryCode;
        }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }
    }

    public static class PandoraConfig {
        private int searchLimit;

        public int getSearchLimit() {
            return this.searchLimit;
        }

        public void setSearchLimit(int searchLimit) {
            this.searchLimit = searchLimit;
        }
    }

    public static class YouTubeConfig {
        private Boolean oembed;
        private Boolean mirror;
        private java.util.List<String> mirrorProviders;
        private Boolean localDiskCache;
        private String diskCachePath;
        private String cipherUrl;

        public Boolean getOembed() {
            return this.oembed;
        }

        public void setOembed(Boolean oembed) {
            this.oembed = oembed;
        }

        public Boolean getMirror() {
            return this.mirror;
        }

        public void setMirror(Boolean mirror) {
            this.mirror = mirror;
        }

        public java.util.List<String> getMirrorProviders() {
            return this.mirrorProviders;
        }

        public void setMirrorProviders(java.util.List<String> mirrorProviders) {
            this.mirrorProviders = mirrorProviders;
        }

        public Boolean getLocalDiskCache() {
            return this.localDiskCache;
        }

        public void setLocalDiskCache(Boolean localDiskCache) {
            this.localDiskCache = localDiskCache;
        }

        public String getDiskCachePath() {
            return this.diskCachePath;
        }

        public void setDiskCachePath(String diskCachePath) {
            this.diskCachePath = diskCachePath;
        }

        public String getCipherUrl() {
            return this.cipherUrl;
        }

        public void setCipherUrl(String cipherUrl) {
            this.cipherUrl = cipherUrl;
        }

        private Long maxDiskCacheMb;

        public Long getMaxDiskCacheMb() {
            return this.maxDiskCacheMb;
        }

        public void setMaxDiskCacheMb(Long maxDiskCacheMb) {
            this.maxDiskCacheMb = maxDiskCacheMb;
        }
    }
}
