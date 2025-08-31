import Foundation

@objc public class SpeechAndText: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
