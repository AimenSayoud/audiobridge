import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins

enum QRCode {
    /// CoreImage renders a 1-module-per-pixel image; it is scaled with nearest
    /// neighbour so the modules stay hard-edged. Interpolating them is what
    /// makes a QR on screen refuse to scan.
    static func image(for text: String, side: CGFloat = 220) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }

        let scale = side / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        let context = CIContext(options: [.useSoftwareRenderer: true])
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return NSImage(cgImage: cgImage, size: NSSize(width: side, height: side))
    }
}
