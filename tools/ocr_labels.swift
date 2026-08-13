// 그림에서 글자와 그 자리를 읽는다.
//
// 서울교통공사가 내놓은 공식 노선도는 벡터이지만 역 이름이 글꼴이 아니라
// 외곽선으로 변환돼 있어 pdftotext가 여덟 낱말만 뽑는다. 자리는 벡터에서
// 캐낼 수 있어도 그 자리가 어느 역인지는 이름을 읽어야 알 수 있다.
// macOS Vision이 한국어를 읽으니 그림에서 읽어 온다.
//
// 쓰기:
//   swift tools/ocr_labels.swift 그림.png > labels.json
//
// 내놓는 것: [{"t":"강남","x":1234.5,"y":678.9,"w":40,"h":12,"c":0.93}, …]
// 좌표는 그림 왼쪽 위를 원점으로 한 픽셀이다(Vision은 왼쪽 아래가 원점이라 뒤집는다).

import Foundation
import Vision
import CoreGraphics
import ImageIO

let arguments = CommandLine.arguments
guard arguments.count >= 2 else {
    FileHandle.standardError.write("쓰기: swift ocr_labels.swift 그림.png\n".data(using: .utf8)!)
    exit(2)
}

let url = URL(fileURLWithPath: arguments[1])
guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    FileHandle.standardError.write("그림을 열지 못했습니다: \(url.path)\n".data(using: .utf8)!)
    exit(1)
}

let width = Double(image.width)
let height = Double(image.height)

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.recognitionLanguages = ["ko-KR", "en-US"]
// 역 이름은 사전에 없는 고유명사가 많다. 언어 교정이 오히려 이름을 망친다.
request.usesLanguageCorrection = false

let handler = VNImageRequestHandler(cgImage: image, options: [:])
do {
    try handler.perform([request])
} catch {
    FileHandle.standardError.write("읽기 실패: \(error)\n".data(using: .utf8)!)
    exit(1)
}

var rows: [String] = []
for observation in request.results ?? [] {
    guard let best = observation.topCandidates(1).first else { continue }
    let text = best.string.trimmingCharacters(in: .whitespacesAndNewlines)
    if text.isEmpty { continue }
    let box = observation.boundingBox
    let x = box.midX * width
    // Vision은 왼쪽 아래가 원점이다. 그림 좌표로 뒤집는다.
    let y = (1 - box.midY) * height
    let escaped = text
        .replacingOccurrences(of: "\\", with: "\\\\")
        .replacingOccurrences(of: "\"", with: "\\\"")
    rows.append(String(
        format: "{\"t\":\"%@\",\"x\":%.1f,\"y\":%.1f,\"w\":%.1f,\"h\":%.1f,\"c\":%.3f}",
        escaped, x, y, box.width * width, box.height * height, best.confidence
    ))
}
print("[\n" + rows.joined(separator: ",\n") + "\n]")
