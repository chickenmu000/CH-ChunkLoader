# CH-ChunkLoader

Paper 1.21.x 용 **청크로더 플러그인**입니다. 청크로더 아이템을 설치하면 그 위치에 **신호기를 머리에 쓴 투명 아머스탠드**가 고정되고, **설치한 위치 기준 범위의 청크가 계속 로드**됩니다. 기본은 3x3 청크이며, `config.yml` 에서 **원하는 범위의 청크로더 종류를 얼마든지 추가**할 수 있고 메시지는 `lang.yml` 로 **한국어 / English / 中文 / Español** 중에서 고를 수 있습니다.

## 요구 사항
- Paper 1.21.x / Java 21

## 사용법
1. 관리자가 청크로더로 쓸 아이템을 손에 들고 `/chunkloaderitem [종류]` (아무 아이템이나 가능, 이름/설명/모델 데이터 포함해서 저장됨. 종류를 생략하면 `default` = 3x3)
2. 그 아이템을 **블록에 우클릭**하면 클릭한 면 위치에 청크로더가 설치됩니다. (상자 등 상호작용 블록은 쉬프트+우클릭)
3. 설치된 청크로더(신호기)를 **우클릭**하면 1줄 GUI 가 열리고, **청크로더 부수기** 버튼을 누르면 철거되며 아이템이 인벤토리로 돌아옵니다. (인벤토리가 가득 차면 발밑에 떨어짐)

## 동작
- 아머스탠드는 투명 / 무적 / 중력 없음 / 이동 불가 로 고정되며 장비 조작과 공격은 막혀 있습니다.
- 청크 로딩은 Paper 의 플러그인 청크 티켓(`addPluginChunkTicket`)으로 처리하며, 서버를 재시작해도 `loaders.yml` 기준으로 다시 적용됩니다.
- 청크로더끼리 로드 범위가 겹쳐도 하나를 철거할 때 다른 청크로더가 쓰는 청크는 계속 로드됩니다.
- 철거는 **설치한 사람 또는 `chunkloader.admin` 권한자**만 가능합니다.
- 월드가 나중에 로드되는 경우(멀티버스 등)에도 월드 로드 시점에 자동으로 적용됩니다.

## 명령어 / 권한
| 명령어 | 설명 | 권한 |
| --- | --- | --- |
| `/chunkloaderitem [종류]` (별칭 `/손에든아이템청크로더설정`) | 손에 든 아이템을 해당 종류의 청크로더 아이템으로 설정 (종류 탭 완성) | `chunkloader.admin` (기본 OP) |

## 청크로더 종류 / 범위 설정 (config.yml)
```yaml
language: ko       # ko / en / zh / es
max-radius: 8      # radius 상한 (서버 부하 방지)
loaders:
  default:         # 종류 이름 = 키 이름
    radius: 1      # 1 = 3x3
    item: null     # /손에든아이템청크로더설정 default 로 자동 저장
  wide:
    radius: 2      # 2 = 5x5
    item: null
```
- `radius` 는 설치한 위치의 청크에서 사방으로 몇 청크까지인지입니다. `0` = 1x1, `1` = 3x3, `2` = 5x5, `3` = 7x7 … (`(2*radius+1)` x `(2*radius+1)`)
- 종류는 `loaders:` 아래에 키를 추가하면 늘어납니다. 예) `big: { radius: 4 }` 추가 후 서버 재시작 → 아이템을 들고 `/chunkloaderitem big`
- 종류마다 아이템이 따로 저장되며, 설치/철거(아이템 반환) 모두 그 종류 기준으로 동작합니다.
- 이미 설치된 청크로더는 서버 재시작 때 config 의 현재 `radius` 로 다시 적용됩니다. (config 에서 종류를 지운 경우엔 설치 당시 radius 유지)
- 1.0 에서 저장한 청크로더 아이템은 자동으로 `default` 종류로 옮겨집니다. 기존 `loaders.yml` 도 그대로 사용됩니다. (3x3)

## 언어 설정 (lang.yml)
- `config.yml` 의 `language` 를 `ko` / `en` / `zh` / `es` 중 하나로 설정하고 서버를 재시작하면 적용됩니다.
- `plugins/CH-ChunkLoader/lang.yml` 에서 문구를 직접 고칠 수 있고(색코드 `&`, 자리표시자 `{size}` 등 유지), 최상위 키(예: `ja:`)를 추가하면 다른 언어도 만들 수 있습니다.
- lang.yml 에 키가 빠져 있으면 플러그인 내장 기본 문구로 대체됩니다.

## 데이터 파일
- `plugins/CH-ChunkLoader/config.yml` : 언어 / 청크로더 종류와 아이템 (아이템은 명령어로 자동 저장)
- `plugins/CH-ChunkLoader/lang.yml` : 언어별 메시지
- `plugins/CH-ChunkLoader/loaders.yml` : 설치된 청크로더 목록 (월드 / 좌표 / 설치자 / 종류 / radius)

## 빌드
```
mvn package
```
`target/CH-ChunkLoader.jar` 를 서버 `plugins/` 에 넣으면 됩니다.

## 참고
- 아머스탠드가 `/kill` 등으로 사라져도 `loaders.yml` 기록은 남아 청크는 계속 로드됩니다. 이 경우 같은 자리에 새로 설치하려면 `loaders.yml` 에서 해당 항목을 지워 주세요.
