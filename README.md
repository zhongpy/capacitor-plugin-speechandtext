# capacitor-plugin-speechandtext

This plugin was used by generate the speech, recognize the speech to text.

## Install

```bash
npm install capacitor-plugin-speechandtext
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)
* [`InitSTT(...)`](#initstt)
* [`DestroySTT()`](#destroystt)
* [`startRecording()`](#startrecording)
* [`stopRecording()`](#stoprecording)
* [`checkPermission()`](#checkpermission)
* [`InitTTS(...)`](#inittts)
* [`generateSpeech(...)`](#generatespeech)
* [`addListener('onRecognizerResult', ...)`](#addlisteneronrecognizerresult-)
* [`addListener('onGenerationComplete', ...)`](#addlistenerongenerationcomplete-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => any
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>any</code>

--------------------


### InitSTT(...)

```typescript
InitSTT(options: { itype: number; rootDir: string; }) => any
```

| Param         | Type                                             |
| ------------- | ------------------------------------------------ |
| **`options`** | <code>{ itype: number; rootDir: string; }</code> |

**Returns:** <code>any</code>

--------------------


### DestroySTT()

```typescript
DestroySTT() => any
```

**Returns:** <code>any</code>

--------------------


### startRecording()

```typescript
startRecording() => any
```

**Returns:** <code>any</code>

--------------------


### stopRecording()

```typescript
stopRecording() => any
```

**Returns:** <code>any</code>

--------------------


### checkPermission()

```typescript
checkPermission() => any
```

**Returns:** <code>any</code>

--------------------


### InitTTS(...)

```typescript
InitTTS(options: { itype: number; rootDir: string; }) => any
```

| Param         | Type                                             |
| ------------- | ------------------------------------------------ |
| **`options`** | <code>{ itype: number; rootDir: string; }</code> |

**Returns:** <code>any</code>

--------------------


### generateSpeech(...)

```typescript
generateSpeech(options: GenerateSpeechOptions) => any
```

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code><a href="#generatespeechoptions">GenerateSpeechOptions</a></code> |

**Returns:** <code>any</code>

--------------------


### addListener('onRecognizerResult', ...)

```typescript
addListener(eventName: 'onRecognizerResult', listenerFunc: (data: { text: string; isEndpoint: boolean; }) => void) => any
```

| Param              | Type                                                                   |
| ------------------ | ---------------------------------------------------------------------- |
| **`eventName`**    | <code>'onRecognizerResult'</code>                                      |
| **`listenerFunc`** | <code>(data: { text: string; isEndpoint: boolean; }) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### addListener('onGenerationComplete', ...)

```typescript
addListener(eventName: 'onGenerationComplete', listenerFunc: (data: GenerateSpeechResult) => void) => any
```

| Param              | Type                                                                                     |
| ------------------ | ---------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'onGenerationComplete'</code>                                                      |
| **`listenerFunc`** | <code>(data: <a href="#generatespeechresult">GenerateSpeechResult</a>) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### Interfaces


#### GenerateSpeechOptions

| Prop                    | Type                 |
| ----------------------- | -------------------- |
| **`text`**              | <code>string</code>  |
| **`wavName`**           | <code>string</code>  |
| **`sid`**               | <code>number</code>  |
| **`speed`**             | <code>number</code>  |
| **`addExplicitMarker`** | <code>boolean</code> |
| **`label`**             | <code>string</code>  |
| **`contentProducer`**   | <code>string</code>  |
| **`produceId`**         | <code>string</code>  |
| **`contentPropagator`** | <code>string</code>  |
| **`propagateId`**       | <code>string</code>  |
| **`reservedCode2`**     | <code>string</code>  |


#### GenerateSpeechResult

| Prop                      | Type                                                  |
| ------------------------- | ----------------------------------------------------- |
| **`filePath`**            | <code>string</code>                                   |
| **`sampleRate`**          | <code>number</code>                                   |
| **`numSamples`**          | <code>number</code>                                   |
| **`explicitMarkerAdded`** | <code>boolean</code>                                  |
| **`aigcMetadata`**        | <code><a href="#aigcmetadata">AigcMetadata</a></code> |
| **`aigcMetadataJson`**    | <code>string</code>                                   |


#### AigcMetadata

| Prop                    | Type                |
| ----------------------- | ------------------- |
| **`Label`**             | <code>string</code> |
| **`ContentProducer`**   | <code>string</code> |
| **`ProduceID`**         | <code>string</code> |
| **`ReservedCode1`**     | <code>string</code> |
| **`ContentPropagator`** | <code>string</code> |
| **`PropagateID`**       | <code>string</code> |
| **`ReservedCode2`**     | <code>string</code> |


#### PluginListenerHandle

| Prop         | Type                      |
| ------------ | ------------------------- |
| **`remove`** | <code>() =&gt; any</code> |

</docgen-api>
