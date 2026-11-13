export interface WallpaperPreset {
  id: string
  name: string
}

export const WALLPAPER_PRESETS: WallpaperPreset[] = [
  { id: 'aurora', name: '极光' },
  { id: 'dawn', name: '晨雾' },
  { id: 'ocean', name: '深海' },
  { id: 'graphite', name: '石墨' }
]

export interface PreparedWallpaper {
  blob: Blob
  fileName: string
}

/**
 * Resize and compress a user image before uploading it to the server. The
 * never receives an unbounded phone photo, and every account gets a fast
 * network-backed wallpaper on any device.
 */
export async function prepareWallpaperUpload(file: File): Promise<PreparedWallpaper> {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  const dataUrl = await readAsDataUrl(file)
  const image = await loadImage(dataUrl)
  const maxDim = 2560
  const scale = Math.min(1, maxDim / Math.max(image.width, image.height))
  const width = Math.max(1, Math.round(image.width * scale))
  const height = Math.max(1, Math.round(image.height * scale))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) {
    return { blob: file, fileName: safeFileName(file.name) }
  }
  context.drawImage(image, 0, 0, width, height)
  const blob = await canvasToBlob(canvas)
  return { blob, fileName: safeFileName(file.name) }
}

function safeFileName(name: string): string {
  const base = name.replace(/\.[^.]+$/, '').replace(/[^\w一-龥-]/g, '_')
  return `${base || 'wallpaper'}.jpg`
}

function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('图片压缩失败'))
    }, 'image/jpeg', 0.86)
  })
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('无法读取图片'))
    reader.readAsDataURL(file)
  })
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('无法解析图片'))
    image.src = src
  })
}
