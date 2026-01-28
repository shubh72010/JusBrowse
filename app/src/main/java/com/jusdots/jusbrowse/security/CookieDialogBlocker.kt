package com.jusdots.jusbrowse.security

/**
 * Layer 7: Cosmetic Filtering
 * Intelligent blocking of cookie consent dialogs and banners
 */
object CookieDialogBlocker {

    /**
     * JavaScript code to inject for blocking cookie banners.
     * Uses a combination of CSS hiding and DOM mutation observation.
     */
    val blockerScript: String = """
        (function() {
            'use strict';
            
            // 1. CSS Hiding Rules
            // Common selectors for cookie banners
            const cssRules = `
                #onetrust-banner-sdk, .onetrust-banner-sdk,
                #cookie-banner, .cookie-banner,
                #cookie-consent, .cookie-consent,
                #gdpr-banner, .gdpr-banner,
                .cc-banner, .cc-window,
                [id*="cookie" i], [class*="cookie" i],
                [id*="consent" i], [class*="consent" i],
                [aria-label*="cookie" i], [aria-label*="consent" i]
            ` + "{ display: none !important; visibility: hidden !important; opacity: 0 !important; pointer-events: none !important; }";
            
            const style = document.createElement('style');
            style.type = 'text/css';
            style.appendChild(document.createTextNode(cssRules));
            document.head.appendChild(style);
            
            // 2. DOM Mutation Observer for dynamic banners
            // Sometimes banners are inserted after page load or don't match simple CSS selectors effectively
            const observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    mutation.addedNodes.forEach(function(node) {
                        if (node.nodeType === 1) { // ELEMENT_NODE
                            // Check ID and Class for keywords
                            const id = node.id ? node.id.toLowerCase() : '';
                            const className = node.className ? (typeof node.className === 'string' ? node.className.toLowerCase() : '') : '';
                            const text = node.innerText ? node.innerText.toLowerCase().substring(0, 100) : '';
                            
                            // Keywords to detect cookie banners
                            if (
                                (id.includes('cookie') || id.includes('consent') || id.includes('gdpr')) ||
                                (className.includes('cookie') || className.includes('consent') || className.includes('gdpr')) ||
                                (text.includes('cookie') && (text.includes('accept') || text.includes('agree')))
                            ) {
                                // Double check it's not the main content (heuristic: z-index is usually high, position fixed/absolute)
                                const style = window.getComputedStyle(node);
                                if (style.position === 'fixed' || style.position === 'absolute' || parseInt(style.zIndex) > 100) {
                                    node.style.setProperty('display', 'none', 'important');
                                    node.style.setProperty('visibility', 'hidden', 'important');
                                }
                            }
                        }
                    });
                });
            });
            
            observer.observe(document.body, { childList: true, subtree: true });
            
            console.log('[JusBrowse] Cookie Blocker Active');
        })();
    """.trimIndent()
}
