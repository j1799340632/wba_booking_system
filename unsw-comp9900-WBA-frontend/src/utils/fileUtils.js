/**
 * Given a js file object, convert it to base64
 * @param {File} file The file to be read.
 * @return {Promise<unknown>} Base64 string
 */
function fileToBase64(file) {
  const validFileTypes = ['image/jpeg', 'image/png', 'image/jpg', 'image/svg+xml', 'image/svg+xml']
  const valid = validFileTypes.find(type => type === file.type);
  // Bad data, let's walk away.
  if (!valid) {
    throw Error('provided file is not a png, jpg or jpeg image.');
  }

  const reader = new FileReader();
  const dataUrlPromise = new Promise((resolve,reject) => {
    reader.onerror = reject;
    reader.onload = () => resolve(reader.result);
  });
  reader.readAsDataURL(file);
  return dataUrlPromise;
}

/**
 * Download File (not suitable for downloading a large file)
 * @param blob blob data
 * @param fileName Expected filename (optional; not required if the backend has a Content-Disposition set)
 */
function downloadFile(blob, fileName) {
  const link = document.createElement('a')
  link.style.display = 'none'
  link.download = fileName

  const objectUrl = URL.createObjectURL(blob)
  link.href = objectUrl

  document.body.appendChild(link)
  link.click()

  document.body.removeChild(link)

  setTimeout(() => {
    URL.revokeObjectURL(objectUrl)
  }, 100);
}

export { fileToBase64, downloadFile }
