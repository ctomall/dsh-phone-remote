/**
 * QR rendering for pairing URLs. The vendored `qrcode-generator` library
 * (MIT, Kazuhiko Arase) builds the symbol; this module turns it into the SVG
 * data URL the settings page shows and the pairing page never needs.
 *
 * @module @local/phone-remote/qr
 */
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const qrcode = require('./vendor/qrcode.cjs')

/**
 * Build one QR code at a readable size.
 * @param {string} text - payload, at most a few hundred bytes.
 * @param {{cellSize?: number, margin?: number}} [options] - rendering options.
 * @returns {string} SVG markup.
 */
export function qrSvg(text, options = {}) {
  const qr = qrcode(0, 'M')
  qr.addData(String(text))
  qr.make()
  return qr.createSvgTag({
    cellSize: options.cellSize ?? 7,
    margin: options.margin ?? 3,
    scalable: true,
    alt: '配对二维码',
    title: '手机远程配对二维码',
  })
}

/**
 * Build an SVG data URL for an `<img src>`.
 * @param {string} text - payload.
 * @param {{cellSize?: number, margin?: number}} [options] - rendering options.
 * @returns {string} data URL.
 */
export function qrDataUrl(text, options = {}) {
  const svg = qrSvg(text, options)
  return `data:image/svg+xml;base64,${Buffer.from(svg, 'utf8').toString('base64')}`
}
