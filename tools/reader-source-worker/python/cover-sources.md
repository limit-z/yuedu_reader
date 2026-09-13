# Cover Sources

The cover crawler currently uses two public, traceable sources:

- **Open Library**: public search endpoint `https://openlibrary.org/search.json` and cover image service `https://covers.openlibrary.org/`. The manifest stores the Open Library work page and original cover URL.
- **Google Books**: public volumes endpoint `https://www.googleapis.com/books/v1/volumes`. The manifest stores the Google Books `infoLink` and thumbnail URL.

Portrait candidates retain the downloaded source image. Landscape candidates are generated from the same validated source image on a light 1200x675 canvas, and are labeled `derived landscape`; the original source URL remains in the candidate record. This makes it clear that the site did not claim a generated derivative was an original source asset.

Both the standalone script and the Java service enforce HTTPS/HTTP allowlists, public DNS resolution, a 5 MB image limit, and actual image decoding. They do not bypass authentication, CAPTCHA, paywalls, robots restrictions, or rate limits.
