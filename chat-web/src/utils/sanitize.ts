import DOMPurify from 'dompurify';

/**
 * ============================================
 * XSS Sanitization Utility
 * Uses DOMPurify to sanitize user-generated content
 * ============================================
 */

// Configure DOMPurify with safe settings
const purify = DOMPurify(window);

/**
 * Allowed tags for message content
 */
const ALLOWED_TAGS = [
  'p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'code', 'pre',
  'ul', 'ol', 'li', 'a', 'blockquote', 'span'
];

/**
 * Allowed attributes
 */
const ALLOWED_ATTR = [
  'href', 'target', 'rel', 'class', 'data-id'
];

/**
 * Configure DOMPurify
 */
purify.setConfig({
  ALLOWED_TAGS,
  ALLOWED_ATTR,
  ALLOW_DATA_ATTR: false,
  FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover'],
  ADD_ATTR: ['target'], // Add target for links
  ADD_TAGS: ['target']
});

/**
 * Sanitize HTML string
 * Removes XSS vectors while preserving safe formatting
 * @param html - HTML string to sanitize
 * @returns Sanitized HTML string
 */
export function sanitizeHtml(html: string): string {
  if (!html || typeof html !== 'string') {
    return '';
  }

  return purify.sanitize(html, {
    RETURN_TRUSTED_TYPE: false
  }) as string;
}

/**
 * Sanitize plain text (escape HTML entities)
 * @param text - Plain text to sanitize
 * @returns Sanitized text
 */
export function sanitizeText(text: string): string {
  if (!text || typeof text !== 'string') {
    return '';
  }

  // Escape HTML entities
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * Sanitize URL
 * @param url - URL to sanitize
 * @returns Sanitized URL or empty string
 */
export function sanitizeUrl(url: string): string {
  if (!url || typeof url !== 'string') {
    return '';
  }

  // Only allow http, https, and mailto protocols
  const validProtocols = ['http:', 'https:', 'mailto:'];
  try {
    const parsed = new URL(url, window.location.origin);
    if (validProtocols.includes(parsed.protocol)) {
      return parsed.href;
    }
  } catch {
    // Invalid URL
  }

  return '';
}

/**
 * Sanitize user display name
 * @param name - Display name to sanitize
 * @returns Sanitized name
 */
export function sanitizeDisplayName(name: string): string {
  if (!name || typeof name !== 'string') {
    return '';
  }

  // Remove control characters and trim
  return name.replace(/[\x00-\x1F\x7F]/g, '').trim().slice(0, 100);
}

/**
 * Validate and sanitize message content
 * Used before sending to server or storing
 * @param content - Message content
 * @returns Sanitized content
 */
export function sanitizeMessageContent(content: string): string {
  if (!content || typeof content !== 'string') {
    return '';
  }

  // Strip all HTML and convert to plain text
  const textOnly = content.replace(/<[^>]*>/g, '');

  // Remove extra whitespace
  const cleaned = textOnly.replace(/\s+/g, ' ').trim();

  // Limit length
  return cleaned.slice(0, 10000);
}

/**
 * Check if content is empty after sanitization
 * @param content - Content to check
 * @returns True if content is effectively empty
 */
export function isEmptyContent(content: string): boolean {
  const sanitized = content.replace(/[\s\n\r\t]/g, '');
  return sanitized.length === 0;
}

/**
 * Create safe HTML for rendering
 * Combines sanitization with optional linkification
 * @param text - Text to convert to safe HTML
 * @param linkify - Whether to convert URLs to links
 * @returns Safe HTML string
 */
export function createSafeHtml(text: string, linkify = true): string {
  if (!text) return '';

  // First sanitize to remove any existing HTML
  let html = sanitizeText(text);

  if (linkify) {
    // Convert URLs to links (with sanitization)
    const urlRegex = /(https?:\/\/[^\s]+)/g;
    html = html.replace(urlRegex, (url) => {
      const safeUrl = sanitizeUrl(url);
      if (safeUrl) {
        return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer" class="text-primary-600 hover:underline">${url}</a>`;
      }
      return url;
    });
  }

  return html;
}

/**
 * Strip all HTML and return plain text
 * @param html - HTML string
 * @returns Plain text
 */
export function stripHtml(html: string): string {
  if (!html) return '';

  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  return tmp.textContent || tmp.innerText || '';
}