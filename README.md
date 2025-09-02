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
* [`InitSTT()`](#initstt)
* [`startRecording()`](#startrecording)
* [`stopRecording()`](#stoprecording)
* [`checkPermission()`](#checkpermission)
* [`InitTTS()`](#inittts)
* [`generateSpeech(...)`](#generatespeech)

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


### InitSTT()

```typescript
InitSTT() => any
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


### InitTTS()

```typescript
InitTTS() => any
```

**Returns:** <code>any</code>

--------------------


### generateSpeech(...)

```typescript
generateSpeech(options: { text: string; sid: number; speed: number; }) => any
```

| Param         | Type                                                       |
| ------------- | ---------------------------------------------------------- |
| **`options`** | <code>{ text: string; sid: number; speed: number; }</code> |

**Returns:** <code>any</code>

--------------------

</docgen-api>
