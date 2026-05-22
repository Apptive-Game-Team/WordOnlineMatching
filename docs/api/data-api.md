# Data API

## Get Parameters

### `GET /api/data/parameters`

게임 내에서 사용되는 모든 파라미터 데이터를 가져옵니다. 특정 버전을 기준으로 변경 사항이 하나라도 있으면 전체 파라미터 스냅샷을 반환합니다.

### Query Parameters

| Name             | Type   | Description                                                                                             | Required |
|------------------|--------|---------------------------------------------------------------------------------------------------------|----------|
| `currentVersion` | String | ISO-8601 형식의 날짜-시간 문자열 (e.g., `2023-01-01T12:00:00`). 이 시간 이후에 변경 사항이 하나라도 있으면 전체 파라미터 스냅샷을 반환합니다. | No       |

### Response Body

성공 시, `ParametersResponse` 객체를 반환합니다.

```json
{
  "parameters": [
    {
      "gameObjectName": "Player",
      "paramName": "MaxHP",
      "value": 100.0
    },
    {
      "gameObjectName": "Player",
      "paramName": "AttackPower",
      "value": 10.0
    }
  ],
  "version": "2023-01-01T15:30:00"
}
```

-   **parameters**: `Parameter` 객체의 배열
    -   `gameObjectName` (String): 파라미터가 속한 게임 객체의 이름.
    -   `paramName` (String): 파라미터의 이름.
    -   `value` (Double): 파라미터의 값.
-   **version** (String): 응답에 포함된 파라미터 중 가장 마지막에 업데이트된 시간 (ISO-8601 형식). `currentVersion` 파라미터가 제공되었지만 새로운 데이터가 없는 경우, 제공된 `currentVersion` 값이 그대로 반환될 수 있습니다. 변경 사항이 하나라도 감지되면 전체 파라미터 스냅샷이 반환되며, 버전은 그 스냅샷의 최신 타임스탬프가 됩니다.

### Example Usage

#### 모든 파라미터 가져오기

`GET /api/data/parameters`

#### 특정 버전 이후 변경이 있으면 전체 파라미터 가져오기

`GET /api/data/parameters?currentVersion=2023-01-01T12:00:00`
