export function showToast(message, type = 'error') {
  if (!message) return
  window.dispatchEvent(new CustomEvent('app-toast', {
    detail: { message, type }
  }))
}

export function toastSuccess(message) {
  showToast(message, 'success')
}

export function toastError(message) {
  showToast(message, 'error')
}

export function toastInfo(message) {
  showToast(message, 'info')
}
