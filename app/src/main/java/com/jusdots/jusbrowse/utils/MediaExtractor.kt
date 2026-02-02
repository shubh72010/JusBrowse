package com.jusdots.jusbrowse.utils

/**
 * JavaScript utility to extract all media (images, videos, audio) from the current webpage.
 * Returns JSON string with categorized media items.
 */
object MediaExtractor {
    
    const val EXTRACT_MEDIA_SCRIPT = """
        (function() {
            const media = {
                images: [],
                videos: [],
                audio: []
            };
            
            // Extract images from <img> tags
            document.querySelectorAll('img').forEach(function(img) {
                if (img.src && !img.src.startsWith('data:') && img.src.startsWith('http')) {
                    media.images.push({
                        url: img.src,
                        title: img.alt || img.title || '',
                        metadata: img.naturalWidth + 'x' + img.naturalHeight
                    });
                }
            });
            
            // Extract background images from CSS
            document.querySelectorAll('*').forEach(function(el) {
                try {
                    const bg = window.getComputedStyle(el).backgroundImage;
                    if (bg && bg !== 'none' && bg.includes('url(')) {
                        const urlMatch = bg.match(/url\(["']?(.+?)["']?\)/);
                        if (urlMatch && urlMatch[1] && !urlMatch[1].startsWith('data:') && urlMatch[1].startsWith('http')) {
                            media.images.push({
                                url: urlMatch[1],
                                title: 'Background Image',
                                metadata: ''
                            });
                        }
                    }
                } catch(e) {}
            });
            
            // Extract videos from <video> tags
            document.querySelectorAll('video').forEach(function(video) {
                if (video.src && video.src.startsWith('http')) {
                    media.videos.push({
                        url: video.src,
                        title: video.title || video.getAttribute('aria-label') || '',
                        metadata: video.duration ? Math.floor(video.duration) + 's' : ''
                    });
                }
                // Check <source> children
                video.querySelectorAll('source').forEach(function(source) {
                    if (source.src && source.src.startsWith('http')) {
                        media.videos.push({
                            url: source.src,
                            title: video.title || video.getAttribute('aria-label') || '',
                            metadata: video.duration ? Math.floor(video.duration) + 's' : ''
                        });
                    }
                });
            });
            
            // Extract audio from <audio> tags
            document.querySelectorAll('audio').forEach(function(audio) {
                if (audio.src && audio.src.startsWith('http')) {
                    media.audio.push({
                        url: audio.src,
                        title: audio.title || audio.getAttribute('aria-label') || '',
                        metadata: audio.duration ? Math.floor(audio.duration) + 's' : ''
                    });
                }
                // Check <source> children
                audio.querySelectorAll('source').forEach(function(source) {
                    if (source.src && source.src.startsWith('http')) {
                        media.audio.push({
                            url: source.src,
                            title: audio.title || audio.getAttribute('aria-label') || '',
                            metadata: audio.duration ? Math.floor(audio.duration) + 's' : ''
                        });
                    }
                });
            });
            
            // Remove duplicates
            media.images = Array.from(new Set(media.images.map(JSON.stringify))).map(JSON.parse);
            media.videos = Array.from(new Set(media.videos.map(JSON.stringify))).map(JSON.parse);
            media.audio = Array.from(new Set(media.audio.map(JSON.stringify))).map(JSON.parse);
            
            return JSON.stringify(media);
        })();
    """
}
